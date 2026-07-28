import { timingSafeEqual, randomUUID } from "node:crypto";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { networkInterfaces } from "node:os";
import { dirname } from "node:path";
import type { EventBus } from "./event-bus.js";

export interface McpDialogImage {
  url: string;
  alt: string;
}

export interface McpDialogRequest {
  requestId: string;
  title: string;
  markdown: string;
  images: McpDialogImage[];
  choices: string[];
  allowText: boolean;
  createdAt: number;
}

export interface McpDialogResponse {
  action: "submit" | "cancel";
  text: string;
  selectedChoices: string[];
}

export interface McpDialogSettings {
  enabled: boolean;
  port: number;
}

interface PendingDialog extends McpDialogRequest {
  resolve: (response: McpDialogResponse) => void;
  reject: (error: Error) => void;
  timer: NodeJS.Timeout;
}

const DEFAULT_SETTINGS: McpDialogSettings = { enabled: false, port: 47832 };
const MAX_BODY_BYTES = 8 * 1024 * 1024;
const DIALOG_TIMEOUT_MS = 30 * 60 * 1000;

export class McpDialogService {
  private settings: McpDialogSettings;
  private server: Server | null = null;
  private readonly pending = new Map<string, PendingDialog>();

  constructor(
    private readonly settingsFile: string,
    private readonly apiToken: string | null,
    private readonly events: EventBus
  ) {
    this.settings = this.loadSettings();
  }

  async startIfEnabled(): Promise<void> {
    if (this.settings.enabled) await this.startListener(this.settings.port);
  }

  status(preferredAddress: string | null = null): McpDialogSettings & { running: boolean; addresses: string[]; url: string | null; prompt: string } {
    const addresses = this.lanAddresses(preferredAddress);
    const url = this.settings.enabled && addresses[0]
      ? `http://${addresses[0]}:${this.settings.port}/mcp`
      : null;
    return {
      ...this.settings,
      running: Boolean(this.server?.listening),
      addresses,
      url,
      prompt: this.configurationPrompt(url)
    };
  }

  async configure(
    settings: McpDialogSettings,
    preferredAddress: string | null = null
  ): Promise<ReturnType<McpDialogService["status"]>> {
    if (!Number.isInteger(settings.port) || settings.port < 1024 || settings.port > 65535) {
      throw new Error("MCP dialog port must be between 1024 and 65535");
    }
    if (settings.enabled && !this.apiToken) throw new Error("Bridge API token is required for LAN MCP dialog");
    const previous = this.settings;
    if (previous.enabled === settings.enabled && previous.port === settings.port) return this.status(preferredAddress);
    this.rejectPending(new Error("MCP dialog service configuration changed"));
    await this.stopListener();
    try {
      this.settings = settings;
      if (settings.enabled) await this.startListener(settings.port);
      this.persistSettings();
      this.events.publish("mcp.dialog.config.updated", null, this.status());
      return this.status(preferredAddress);
    } catch (error) {
      this.settings = previous;
      if (previous.enabled) await this.startListener(previous.port).catch(() => {});
      throw error;
    }
  }

  listPending(): McpDialogRequest[] {
    return [...this.pending.values()]
      .map(({ resolve: _resolve, reject: _reject, timer: _timer, ...request }) => request)
      .sort((left, right) => left.createdAt - right.createdAt);
  }

  respond(requestId: string, response: McpDialogResponse): void {
    const pending = this.pending.get(requestId);
    if (!pending) throw new Error("MCP dialog request is missing or already resolved");
    this.pending.delete(requestId);
    clearTimeout(pending.timer);
    pending.resolve({
      action: response.action,
      text: response.text.slice(0, 100_000),
      selectedChoices: response.selectedChoices.slice(0, 20)
    });
    this.events.publish("mcp.dialog.resolved", null, { requestId, action: response.action });
  }

  async close(): Promise<void> {
    this.rejectPending(new Error("MCP dialog service stopped"));
    await this.stopListener();
  }

  private rejectPending(error: Error): void {
    for (const request of this.pending.values()) {
      clearTimeout(request.timer);
      request.reject(error);
    }
    this.pending.clear();
  }

  private async startListener(port: number): Promise<void> {
    const server = createServer((request, response) => void this.handleRequest(request, response));
    server.requestTimeout = 0;
    server.headersTimeout = 60_000;
    await new Promise<void>((resolve, reject) => {
      const onError = (error: Error) => reject(error);
      server.once("error", onError);
      server.listen(port, "0.0.0.0", () => {
        server.off("error", onError);
        server.on("error", () => {});
        resolve();
      });
    });
    this.server = server;
  }

  private async stopListener(): Promise<void> {
    const server = this.server;
    this.server = null;
    if (!server?.listening) return;
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }

  private async handleRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
    try {
      if (!this.isPrivateAddress(request.socket.remoteAddress ?? "")) return this.send(response, 403, { error: "private_network_only" });
      if (!this.tokenMatches(request.headers.authorization)) return this.send(response, 401, { error: "unauthorized" });
      const path = new URL(request.url ?? "/", "http://localhost").pathname;
      if (request.method === "GET" && path === "/health") return this.send(response, 200, { ok: true });
      if (request.method !== "POST" || path !== "/mcp") return this.send(response, 404, { error: "not_found" });
      const body = await this.readJson(request) as Record<string, unknown>;
      const id = body.id ?? null;
      const method = typeof body.method === "string" ? body.method : "";
      if (method === "notifications/initialized") return this.send(response, 202, {});
      if (method === "initialize") {
        return this.rpc(response, id, {
          protocolVersion: "2025-03-26",
          capabilities: { tools: {} },
          serverInfo: { name: "codex-pocket-dialog", version: "1.0.0" }
        });
      }
      if (method === "ping") return this.rpc(response, id, {});
      if (method === "tools/list") return this.rpc(response, id, { tools: [this.toolDefinition()] });
      if (method === "tools/call") {
        const params = this.record(body.params);
        if (params.name !== "pocket_dialog") return this.rpcError(response, id, -32602, "Unknown tool");
        const dialogResponse = await this.requestDialog(this.record(params.arguments));
        const summary = dialogResponse.action === "cancel"
          ? "用户取消了本次对话请求。"
          : [
              dialogResponse.selectedChoices.length > 0
                ? `用户选择：${dialogResponse.selectedChoices.join("、")}`
                : "",
              dialogResponse.text ? `用户回复：${dialogResponse.text}` : ""
            ].filter(Boolean).join("\n");
        return this.rpc(response, id, {
          content: [{ type: "text", text: summary || "用户已确认。" }],
          isError: dialogResponse.action === "cancel"
        });
      }
      return this.rpcError(response, id, -32601, "Method not found");
    } catch (error) {
      this.send(response, 500, { jsonrpc: "2.0", id: null, error: { code: -32603, message: error instanceof Error ? error.message : String(error) } });
    }
  }

  private requestDialog(argumentsValue: Record<string, unknown>): Promise<McpDialogResponse> {
    if (this.pending.size >= 20) throw new Error("MCP dialog queue is full");
    const requestId = randomUUID();
    const images = Array.isArray(argumentsValue.images)
      ? argumentsValue.images.slice(0, 8).map((value) => this.record(value)).map((value) => ({
          url: String(value.url ?? "").slice(0, 2_000),
          alt: String(value.alt ?? "").slice(0, 300)
        })).filter((value) => /^(https?:|data:image\/)/i.test(value.url))
      : [];
    const choices = Array.isArray(argumentsValue.choices)
      ? argumentsValue.choices.map(String).map((value) => value.trim()).filter(Boolean).slice(0, 12)
      : [];
    return new Promise<McpDialogResponse>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        this.events.publish("mcp.dialog.resolved", null, { requestId, action: "expired" });
        reject(new Error("MCP dialog timed out"));
      }, DIALOG_TIMEOUT_MS);
      const request: PendingDialog = {
        requestId,
        title: String(argumentsValue.title ?? "AI 需要您的确认").slice(0, 300),
        markdown: String(argumentsValue.markdown ?? "").slice(0, 200_000),
        images,
        choices,
        allowText: argumentsValue.allowText !== false,
        createdAt: Date.now(),
        resolve,
        reject,
        timer
      };
      this.pending.set(requestId, request);
      this.events.publish("mcp.dialog.requested", null, { requestId });
    });
  }

  private toolDefinition(): Record<string, unknown> {
    return {
      name: "pocket_dialog",
      description: "在用户的 Codex Pocket 手机上展示 Markdown、图片、选择项和文本输入，并等待用户确认或回复。",
      inputSchema: {
        type: "object",
        properties: {
          title: { type: "string", description: "简短标题" },
          markdown: { type: "string", description: "完整 Markdown 正文" },
          images: {
            type: "array",
            maxItems: 8,
            items: {
              type: "object",
              properties: { url: { type: "string" }, alt: { type: "string" } },
              required: ["url"]
            }
          },
          choices: { type: "array", maxItems: 12, items: { type: "string" } },
          allowText: { type: "boolean", default: true }
        },
        required: ["markdown"]
      }
    };
  }

  private configurationPrompt(url: string | null): string {
    const endpoint = url ?? `http://<电脑局域网IP>:${this.settings.port}/mcp`;
    return [
      "当需要用户确认、补充信息、选择方案或查看图片时，调用 MCP 工具 pocket_dialog。",
      "正文必须使用 Markdown；需要展示图片时传 images；有固定选项时传 choices；需要自由回复时启用 allowText。",
      "必须等待工具返回后再继续，不要替用户选择，不要在工具外重复弹出相同问题。",
      "MCP Streamable HTTP 配置：",
      JSON.stringify({ url: endpoint, headers: { Authorization: `Bearer ${this.apiToken ?? "<Bridge密钥>"}` } }, null, 2)
    ].join("\n\n");
  }

  private lanAddresses(preferredAddress: string | null): string[] {
    const preferred = this.normalizeAddress(preferredAddress ?? "");
    return Object.entries(networkInterfaces())
      .flatMap(([name, entries]) => (entries ?? []).map((entry) => ({ name, entry })))
      .filter(({ entry }) => entry.family === "IPv4" && !entry.internal && this.isLanAddress(entry.address))
      .map(({ name, entry }) => ({ name, address: entry.address }))
      .filter((value, index, all) => all.findIndex((candidate) => candidate.address === value.address) === index)
      .sort((left, right) =>
        this.addressPriority(right.name, right.address, preferred) -
        this.addressPriority(left.name, left.address, preferred)
      )
      .map(({ address }) => address);
  }

  private addressPriority(name: string, address: string, preferred: string): number {
    if (address === preferred) return 10_000;
    let score = address.startsWith("192.168.") ? 300 : address.startsWith("10.") ? 200 : 100;
    if (/wi-?fi|wireless|wlan|ethernet|以太网|无线/i.test(name)) score += 50;
    if (/virtual|vmware|virtualbox|hyper-v|vethernet|wsl|vpn|tunnel|tap|loopback/i.test(name)) score -= 500;
    return score;
  }

  private normalizeAddress(raw: string): string {
    return raw.replace(/^::ffff:/, "");
  }

  private isLanAddress(raw: string): boolean {
    const address = this.normalizeAddress(raw);
    if (address.startsWith("10.") || address.startsWith("192.168.")) return true;
    const match = /^172\.(\d+)\./.exec(address);
    return Boolean(match && Number(match[1]) >= 16 && Number(match[1]) <= 31);
  }

  private loadSettings(): McpDialogSettings {
    try {
      const value = JSON.parse(readFileSync(this.settingsFile, "utf8")) as Partial<McpDialogSettings>;
      const port = Number.isInteger(value.port) && Number(value.port) >= 1024 && Number(value.port) <= 65535
        ? Number(value.port)
        : DEFAULT_SETTINGS.port;
      return { enabled: value.enabled === true, port };
    } catch {
      return { ...DEFAULT_SETTINGS };
    }
  }

  private persistSettings(): void {
    mkdirSync(dirname(this.settingsFile), { recursive: true });
    const temporary = `${this.settingsFile}.tmp`;
    writeFileSync(temporary, JSON.stringify(this.settings, null, 2), "utf8");
    renameSync(temporary, this.settingsFile);
  }

  private readJson(request: IncomingMessage): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const chunks: Buffer[] = [];
      let size = 0;
      request.on("data", (chunk: Buffer) => {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) {
          reject(new Error("MCP request body is too large"));
          request.destroy();
          return;
        }
        chunks.push(chunk);
      });
      request.on("end", () => {
        try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); }
        catch { reject(new Error("Invalid JSON")); }
      });
      request.on("error", reject);
    });
  }

  private record(value: unknown): Record<string, any> {
    return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, any> : {};
  }

  private rpc(response: ServerResponse, id: unknown, result: unknown): void {
    this.send(response, 200, { jsonrpc: "2.0", id, result });
  }

  private rpcError(response: ServerResponse, id: unknown, code: number, message: string): void {
    this.send(response, 200, { jsonrpc: "2.0", id, error: { code, message } });
  }

  private send(response: ServerResponse, status: number, value: unknown): void {
    if (response.headersSent) return;
    const body = JSON.stringify(value);
    response.writeHead(status, { "content-type": "application/json; charset=utf-8", "content-length": Buffer.byteLength(body) });
    response.end(body);
  }

  private tokenMatches(header: string | undefined): boolean {
    if (!this.apiToken || !header?.startsWith("Bearer ")) return false;
    const actual = Buffer.from(header.slice(7));
    const expected = Buffer.from(this.apiToken);
    return actual.length === expected.length && timingSafeEqual(actual, expected);
  }

  private isPrivateAddress(raw: string): boolean {
    const address = this.normalizeAddress(raw);
    if (address === "127.0.0.1" || address === "::1") return true;
    return this.isLanAddress(address);
  }
}

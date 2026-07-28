import { execFile, spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { EventEmitter } from "node:events";
import { promisify } from "node:util";
import type { BridgeConfig } from "../config.js";
import type { BridgeState, JsonRpcId, JsonRpcMessage, JsonRpcRequest, JsonRpcResponse } from "../types.js";
import { JsonParser } from "./json-parser.js";
import { NdjsonFramer } from "./ndjson-framer.js";

const execFileAsync = promisify(execFile);

interface PendingRequest {
  method: string;
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

export interface CodexClientEvents {
  notification: [method: string, params: unknown];
  serverRequest: [request: JsonRpcRequest];
  state: [state: BridgeState, detail?: string];
  stderr: [line: string];
}

export class CodexClient extends EventEmitter<CodexClientEvents> implements AsyncDisposable {
  private process: ChildProcessWithoutNullStreams | null = null;
  private readonly parser = new JsonParser();
  private readonly pending = new Map<JsonRpcId, PendingRequest>();
  private readonly framer: NdjsonFramer;
  private nextId = 1;
  private pendingParseBytes = 0;
  private parseChain: Promise<void> = Promise.resolve();
  private startPromise: Promise<void> | null = null;
  private state: BridgeState = "stopped";
  private stopping = false;
  private restartAttempt = 0;
  private restartTimer: NodeJS.Timeout | null = null;
  private initializedResult: unknown = null;

  constructor(private readonly config: BridgeConfig) {
    super();
    this.framer = new NdjsonFramer(config.maxLineBytes, (line, bytes) => this.enqueueLine(line, bytes));
  }

  get currentState(): BridgeState {
    return this.state;
  }

  get initializationResult(): unknown {
    return this.initializedResult;
  }

  async start(): Promise<void> {
    if (this.state === "ready") return;
    if (this.startPromise) return this.startPromise;
    this.stopping = false;
    this.startPromise = this.startInternal().finally(() => {
      this.startPromise = null;
    });
    return this.startPromise;
  }

  private async startInternal(): Promise<void> {
    this.setState(this.restartAttempt === 0 ? "starting" : "restarting");
    const useManagedDaemon = process.platform !== "win32";
    if (useManagedDaemon) {
      await execFileAsync(this.config.codexBin, ["app-server", "daemon", "start"], {
        windowsHide: true,
        timeout: 30_000,
        maxBuffer: 2 * 1024 * 1024
      });
    }

    if (this.stopping) return;
    const args = useManagedDaemon
      ? ["app-server", "proxy"]
      : ["-c", "features.code_mode_host=true", "app-server", "--analytics-default-enabled", "--stdio"];
    const child = spawn(this.config.codexBin, args, {
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
      env: { ...process.env, RUST_LOG: process.env.RUST_LOG || "warn" }
    });
    this.process = child;
    child.stdout.on("data", (chunk: Buffer) => {
      try {
        this.framer.push(chunk);
      } catch (error) {
        this.handleTransportFailure(error instanceof Error ? error : new Error(String(error)));
      }
    });
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => {
      for (const line of chunk.split(/\r?\n/)) if (line.trim()) this.emit("stderr", line.trim());
    });
    child.on("error", (error) => this.handleTransportFailure(error));
    child.on("exit", (code, signal) => {
      if (this.process === child) this.process = null;
      if (!this.stopping) this.handleTransportFailure(new Error(`Codex proxy exited code=${code} signal=${signal}`));
    });

    const initialized = await this.requestInternal(
      "initialize",
      {
        clientInfo: { name: "codex-mobile-bridge", title: "Codex Mobile Bridge", version: "0.1.0" },
        capabilities: { experimentalApi: true, requestAttestation: false, mcpServerOpenaiFormElicitation: true }
      },
      30_000,
      true
    );
    this.initializedResult = initialized;
    this.writeMessage({ method: "initialized" });
    this.restartAttempt = 0;
    this.setState("ready", JSON.stringify(initialized));
  }

  async request<T>(method: string, params: unknown, timeoutMs = this.config.requestTimeoutMs): Promise<T> {
    await this.start();
    if (this.state !== "ready") throw new Error(`Codex is not ready: ${this.state}`);
    return this.requestInternal(method, params, timeoutMs, false) as Promise<T>;
  }

  respond(id: JsonRpcId, result: unknown): void {
    if (this.state !== "ready") throw new Error("Codex is not ready");
    this.writeMessage({ id, result });
  }

  respondError(id: JsonRpcId, error: { code: number; message: string; data?: unknown }): void {
    if (this.state !== "ready") throw new Error("Codex is not ready");
    this.writeMessage({ id, error });
  }

  notify(method: string, params?: unknown): void {
    if (this.state !== "ready") throw new Error("Codex is not ready");
    this.writeMessage(params === undefined ? { method } : { method, params });
  }

  private requestInternal(method: string, params: unknown, timeoutMs: number, allowStarting: boolean): Promise<unknown> {
    if (!this.process || (!allowStarting && this.state !== "ready")) {
      return Promise.reject(new Error(`Cannot send ${method}: transport unavailable`));
    }
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Codex request timed out: ${method}`));
      }, timeoutMs);
      timeout.unref();
      this.pending.set(id, { method, resolve, reject, timeout });
      try {
        this.writeMessage({ id, method, params });
      } catch (error) {
        clearTimeout(timeout);
        this.pending.delete(id);
        reject(error);
      }
    });
  }

  private writeMessage(message: object): void {
    const stdin = this.process?.stdin;
    if (!stdin || stdin.destroyed || !stdin.writable) throw new Error("Codex stdin is not writable");
    const payload = `${JSON.stringify(message)}\n`;
    const payloadBytes = Buffer.byteLength(payload);
    if (stdin.writableLength + payloadBytes > this.config.maxPendingBytes) {
      throw new Error("Codex stdin backpressure limit reached");
    }
    const accepted = stdin.write(payload, "utf8");
    if (!accepted) stdin.once("drain", () => undefined);
  }

  private enqueueLine(line: string, bytes: number): void {
    this.pendingParseBytes += bytes;
    if (this.pendingParseBytes > this.config.maxPendingBytes) this.process?.stdout.pause();

    this.parseChain = this.parseChain
      .then(async () => {
        const message = (await this.parser.parse(line)) as JsonRpcMessage;
        this.handleMessage(message);
      })
      .catch((error) => this.handleTransportFailure(error instanceof Error ? error : new Error(String(error))))
      .finally(() => {
        this.pendingParseBytes -= bytes;
        if (this.pendingParseBytes < this.config.maxPendingBytes / 2) this.process?.stdout.resume();
      });
  }

  private handleMessage(message: JsonRpcMessage): void {
    if ("id" in message && !("method" in message)) {
      const response = message as JsonRpcResponse;
      const pending = this.pending.get(response.id);
      if (!pending) return;
      clearTimeout(pending.timeout);
      this.pending.delete(response.id);
      if (response.error) pending.reject(new Error(`${pending.method}: ${response.error.message}`));
      else pending.resolve(response.result);
      return;
    }

    if ("method" in message && "id" in message) {
      this.emit("serverRequest", message as JsonRpcRequest);
      return;
    }

    if ("method" in message) this.emit("notification", message.method, message.params);
  }

  private handleTransportFailure(error: Error): void {
    if (this.stopping) return;
    this.rejectPending(error);
    const child = this.process;
    this.process = null;
    if (child && !child.killed) child.kill();
    this.framer.reset();
    this.setState("restarting", error.message);
    this.scheduleRestart();
  }

  private scheduleRestart(): void {
    if (this.restartTimer || this.stopping) return;
    const delay = Math.min(30_000, 1_000 * 2 ** Math.min(this.restartAttempt++, 5));
    this.restartTimer = setTimeout(() => {
      this.restartTimer = null;
      void this.start().catch((error) => {
        this.setState("failed", error instanceof Error ? error.message : String(error));
        this.scheduleRestart();
      });
    }, delay);
    this.restartTimer.unref();
  }

  private rejectPending(error: Error): void {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }
    this.pending.clear();
  }

  private setState(state: BridgeState, detail?: string): void {
    this.state = state;
    this.emit("state", state, detail);
  }

  async [Symbol.asyncDispose](): Promise<void> {
    this.stopping = true;
    if (this.restartTimer) clearTimeout(this.restartTimer);
    this.restartTimer = null;
    this.rejectPending(new Error("Codex client stopped"));
    const child = this.process;
    this.process = null;
    if (child && !child.killed) child.kill();
    await this.parser[Symbol.asyncDispose]();
    this.setState("stopped");
  }
}

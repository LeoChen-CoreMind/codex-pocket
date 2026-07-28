import type WebSocket from "ws";
import type { CodexClient } from "../app-server/codex-client.js";
import type { BridgeConfig } from "../config.js";
import type { JsonRpcId, JsonRpcRequest } from "../types.js";
import type { ThreadService } from "./thread-service.js";

type RpcObject = Record<string, unknown> & { id?: JsonRpcId; method?: string; params?: unknown };

export class JsonRpcProxyHub {
  private readonly sockets = new Set<WebSocket>();

  constructor(
    private readonly client: CodexClient,
    private readonly threads: ThreadService,
    private readonly config: BridgeConfig
  ) {
    client.on("notification", (method, params) => this.broadcast({ method, params }));
    client.on("serverRequest", (request) => this.broadcast(request));
  }

  attach(socket: WebSocket): void {
    this.sockets.add(socket);
    socket.on("message", (data, isBinary) => {
      if (isBinary) {
        socket.close(1003, "Text frames only");
        return;
      }
      void this.handle(socket, data.toString()).catch((error) => {
        this.send(socket, {
          id: null,
          error: { code: -32603, message: error instanceof Error ? error.message : String(error) }
        });
      });
    });
    socket.on("close", () => this.sockets.delete(socket));
    socket.on("error", () => this.sockets.delete(socket));
  }

  private async handle(socket: WebSocket, text: string): Promise<void> {
    let message: RpcObject;
    try {
      const parsed = JSON.parse(text) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Expected JSON-RPC object");
      message = parsed as RpcObject;
    } catch (error) {
      this.send(socket, {
        id: null,
        error: { code: -32700, message: error instanceof Error ? error.message : "Invalid JSON" }
      });
      return;
    }

    if (typeof message.method === "string" && message.id !== undefined) {
      await this.handleRequest(socket, message as JsonRpcRequest);
      return;
    }
    if (typeof message.method === "string") {
      if (message.method !== "initialized") this.client.notify(message.method, message.params);
      return;
    }
    if (message.id !== undefined) {
      const error = this.rpcError(message.error);
      const response = error ? { error } : { result: message.result };
      this.threads.tryRespondToInteraction(String(message.id), response, "vscode");
    }
  }

  private async handleRequest(socket: WebSocket, request: JsonRpcRequest): Promise<void> {
    try {
      if (request.method === "initialize") {
        await this.client.start();
        this.send(socket, { id: request.id, result: this.client.initializationResult });
        return;
      }

      // A downstream window must not remove the shared upstream subscription.
      if (request.method === "thread/unsubscribe") {
        this.send(socket, { id: request.id, result: {} });
        return;
      }

      const result = await this.client.request(request.method, request.params ?? {}, 30 * 60 * 1000);
      this.send(socket, { id: request.id, result });
    } catch (error) {
      this.send(socket, {
        id: request.id,
        error: { code: -32000, message: error instanceof Error ? error.message : String(error) }
      });
    }
  }

  private rpcError(value: unknown): { code: number; message: string; data?: unknown } | undefined {
    if (!value || typeof value !== "object") return undefined;
    const error = value as Record<string, unknown>;
    if (typeof error.code !== "number" || typeof error.message !== "string") return undefined;
    return { code: error.code, message: error.message, data: error.data };
  }

  private broadcast(message: object): void {
    for (const socket of this.sockets) this.send(socket, message);
  }

  private send(socket: WebSocket, message: object): void {
    if (socket.readyState !== 1) {
      this.sockets.delete(socket);
      return;
    }
    if (socket.bufferedAmount > this.config.maxWsBufferedBytes) {
      socket.close(1013, "Backpressure limit reached");
      this.sockets.delete(socket);
      return;
    }
    socket.send(JSON.stringify(message), (error) => {
      if (error) this.sockets.delete(socket);
    });
  }
}

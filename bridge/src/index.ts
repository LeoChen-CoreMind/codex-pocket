import { CodexClient } from "./app-server/codex-client.js";
import { loadConfig } from "./config.js";
import { buildServer } from "./server.js";
import { EventBus } from "./services/event-bus.js";
import { ThreadService } from "./services/thread-service.js";
import { VsCodeInstanceService } from "./services/vscode-instance-service.js";
import { McpDialogService } from "./services/mcp-dialog-service.js";
import { createConnection, createServer } from "node:net";

async function main(): Promise<void> {
  const config = loadConfig();
  const client = new CodexClient(config);
  const events = new EventBus(config.eventBufferSize, config.maxWsBufferedBytes);
  const threads = new ThreadService(client, events, config);
  const vscodeInstances = new VsCodeInstanceService(config.vscodeBindingFile, events);
  const mcpDialogs = new McpDialogService(config.mcpDialogSettingsFile, config.apiToken, events);
  const app = await buildServer(config, client, threads, events, vscodeInstances, mcpDialogs);

  client.on("stderr", (line) => app.log.warn({ source: "codex" }, line));

  await client.start();
  await mcpDialogs.startIfEnabled();
  await app.listen({ host: config.host, port: config.port });

  const forwardingServers = [47831, 47816]
    .filter((port) => port !== config.port)
    .map((port) => {
      const server = createServer((incoming) => {
        const upstream = createConnection({ host: "127.0.0.1", port: config.port });
        incoming.pipe(upstream);
        upstream.pipe(incoming);
        incoming.on("error", () => upstream.destroy());
        upstream.on("error", () => incoming.destroy());
      });
      server.on("error", (error) => {
        app.log.warn({ err: error, port }, "Mobile compatibility listener unavailable");
      });
      server.listen({ host: "0.0.0.0", port }, () => {
        app.log.info({ port, targetPort: config.port }, "Mobile compatibility listener ready");
      });
      return server;
    });

  let shuttingDown = false;
  async function shutdown(signal: string): Promise<void> {
    if (shuttingDown) return;
    shuttingDown = true;
    app.log.info({ signal }, "Shutting down");
    events.close();
    vscodeInstances.close();
    await mcpDialogs.close();
    await Promise.all(forwardingServers.map((server) => new Promise<void>((resolve) => {
      if (!server.listening) return resolve();
      server.close(() => resolve());
    })));
    await app.close();
    await client[Symbol.asyncDispose]();
  }

  process.once("SIGINT", () => void shutdown("SIGINT"));
  process.once("SIGTERM", () => void shutdown("SIGTERM"));

  process.on("uncaughtException", (error) => {
    app.log.fatal({ err: error }, "Uncaught exception");
    void shutdown("uncaughtException").finally(() => process.exit(1));
  });

  process.on("unhandledRejection", (error) => {
    app.log.error({ err: error }, "Unhandled rejection");
  });
}

void main().catch((error) => {
  console.error(error);
  process.exit(1);
});

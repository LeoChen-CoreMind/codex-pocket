import { timingSafeEqual } from "node:crypto";
import { resolve } from "node:path";
import websocket from "@fastify/websocket";
import multipart from "@fastify/multipart";
import Fastify, { type FastifyInstance } from "fastify";
import { z } from "zod";
import type { CodexClient } from "./app-server/codex-client.js";
import type { BridgeConfig } from "./config.js";
import { EventBus } from "./services/event-bus.js";
import { ThreadService } from "./services/thread-service.js";
import { JsonRpcProxyHub } from "./services/json-rpc-proxy-hub.js";
import { registerLibreChatCompat } from "./services/librechat-compat.js";
import { AsyncSemaphore } from "./util/semaphore.js";
import { VsCodeInstanceService } from "./services/vscode-instance-service.js";
import { FileStore } from "./services/file-store.js";
import { WorkspaceService } from "./services/workspace-service.js";
import { WorkspaceFtpService } from "./services/workspace-ftp-service.js";
import { McpDialogService } from "./services/mcp-dialog-service.js";

const sendMessageSchema = z.object({
  clientMessageId: z.string().min(8).max(128),
  text: z.string().trim().min(1).max(100_000),
  mode: z.enum(["default", "plan", "agent"]).default("default"),
  model: z.string().min(1).max(200).nullable().optional(),
  reasoningEffort: z.string().min(1).max(50).nullable().optional(),
  fullAccess: z.boolean().default(false),
  fileIds: z.array(z.string().uuid()).max(10).default([]),
  deliveryMode: z.enum(["queue", "steer"]).default("queue"),
  expectedTurnId: z.string().min(1).max(200).nullable().optional()
});

const queueUpdateSchema = z.object({ text: z.string().trim().min(1).max(100_000) });
const queueReorderSchema = z.object({ clientMessageIds: z.array(z.string().min(8).max(128)).max(20) });
const queueSteerSchema = z.object({ expectedTurnId: z.string().min(1).max(200) });
const interruptSchema = z.object({ expectedTurnId: z.string().min(1).max(200) });
const retryPolicySchema = z.object({
  enabled: z.boolean(),
  maxRetries: z.number().int().min(1).max(20).default(3),
  untilSuccess: z.boolean().default(false),
  delaySeconds: z.number().int().min(1).max(300).default(5),
  retryPrompt: z.string().trim().min(1).max(4_000)
});
const mcpDialogConfigSchema = z.object({
  enabled: z.boolean(),
  port: z.number().int().min(1024).max(65535)
});
const mcpDialogResponseSchema = z.object({
  action: z.enum(["submit", "cancel"]),
  text: z.string().max(100_000).default(""),
  selectedChoices: z.array(z.string().max(1_000)).max(20).default([])
});

const interactionResponseSchema = z.object({ result: z.unknown() });
const interactionActionSchema = z.object({
  action: z.enum(["accept", "acceptForSession", "decline"])
});
const interactionAnswersSchema = z.object({
  answers: z.record(z.string().min(1).max(200), z.array(z.string().max(10_000)).max(20))
});
const vscodeRegistrationSchema = z.object({
  instanceId: z.string().uuid(),
  editorName: z.string().min(1).max(200),
  windowTitle: z.string().min(1).max(500),
  workspaceName: z.string().max(300).nullable(),
  workspaceFolders: z.array(z.string().min(1).max(2_000)).max(32),
  processId: z.number().int().positive(),
  extensionHostPid: z.number().int().positive(),
  machineName: z.string().min(1).max(300),
  vscodeVersion: z.string().min(1).max(100),
  codexCliExecutable: z.string().max(4_000).nullable().optional().default(null),
  openThreads: z.array(z.object({
    threadId: z.string().min(1).max(200),
    label: z.string().max(500),
    active: z.boolean(),
    running: z.boolean().default(false),
    terminalStatus: z.enum(["completed", "aborted"]).nullable().default(null),
    activityUpdatedAt: z.number().int().nonnegative().nullable().default(null)
  })).max(100)
});
const vscodeBindingSchema = z.object({ instanceId: z.string().uuid().nullable() });
const vscodeCompletionSchema = z.object({ sequence: z.number().int().nonnegative() });
const vscodeCommandSchema = z.object({
  type: z.enum(["focus", "newChat", "openThread", "closeThread", "openFile"]),
  threadId: z.string().min(1).max(200).optional(),
  path: z.string().min(1).max(4_000).optional(),
  rootId: z.string().length(24).optional()
}).superRefine((value, context) => {
  if ((value.type === "openThread" || value.type === "closeThread") && !value.threadId) {
    context.addIssue({ code: "custom", message: "threadId is required for thread commands" });
  }
  if (value.type === "openFile" && (!value.path || !value.rootId)) {
    context.addIssue({ code: "custom", message: "rootId and path are required for openFile" });
  }
});

function tokenMatches(header: string | undefined, expected: string): boolean {
  if (!header?.startsWith("Bearer ")) return false;
  const actual = Buffer.from(header.slice(7), "utf8");
  const wanted = Buffer.from(expected, "utf8");
  return actual.length === wanted.length && timingSafeEqual(actual, wanted);
}

function isPrivateAddress(rawAddress: string): boolean {
  const address = rawAddress.replace(/^::ffff:/, "");
  if (address === "127.0.0.1" || address === "::1") return true;
  if (address.startsWith("10.") || address.startsWith("192.168.")) return true;
  const match = /^172\.(\d{1,3})\./.exec(address);
  if (match) {
    const second = Number.parseInt(match[1] ?? "0", 10);
    if (second >= 16 && second <= 31) return true;
  }
  return address.startsWith("fc") || address.startsWith("fd") || address.startsWith("fe80:");
}

function integerQuery(value: unknown, fallback: number, max: number): number {
  const parsed = typeof value === "string" ? Number.parseInt(value, 10) : fallback;
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, max);
}

function sameExecutablePath(left: string | null, right: string): boolean {
  if (!left) return false;
  const normalize = (value: string) => {
    const absolute = resolve(value);
    return process.platform === "win32" ? absolute.toLocaleLowerCase("en-US") : absolute;
  };
  return normalize(left) === normalize(right);
}

export async function buildServer(
  config: BridgeConfig,
  client: CodexClient,
  threads: ThreadService,
  events: EventBus,
  vscodeInstances: VsCodeInstanceService,
  mcpDialogs: McpDialogService
): Promise<FastifyInstance> {
  const app = Fastify({
    logger: {
      level: process.env.LOG_LEVEL || "info",
      redact: ["req.headers.authorization", "body.text", "body.result"]
    },
    bodyLimit: 1024 * 1024,
    requestTimeout: 30_000,
    keepAliveTimeout: 72_000,
    connectionTimeout: 10_000
  });
  const reads = new AsyncSemaphore(4, 50, 15_000);
  const vscodeProxy = new JsonRpcProxyHub(client, threads, config);
  const files = new FileStore(config.filesDirectory);
  const workspace = new WorkspaceService(vscodeInstances);
  const ftp = new WorkspaceFtpService(workspace, {
    username: config.ftpUsername,
    password: config.ftpPassword
  });
  await files.initialize();

  await app.register(multipart, {
    limits: { files: 1, fileSize: 20 * 1024 * 1024, fields: 20, parts: 21 }
  });

  await app.register(websocket, {
    options: { maxPayload: 8 * 1024 * 1024, perMessageDeflate: false }
  });

  app.addHook("onRequest", async (request, reply) => {
    if (!isPrivateAddress(request.ip)) return reply.code(403).send({ error: "private_network_only" });
    const publicPath = request.url.split("?", 1)[0];
    if (!config.apiToken || publicPath === "/health" || publicPath === "/api/config" ||
      publicPath === "/api/auth/login" || publicPath === "/api/auth/refresh") return;
    if (!tokenMatches(request.headers.authorization, config.apiToken)) {
      return reply.code(401).send({ error: "unauthorized" });
    }
  });

  app.get("/health", async () => ({
    ok: client.currentState === "ready",
    bridgeState: client.currentState,
    version: "0.1.0",
    timestamp: Date.now()
  }));

  app.post("/internal/shutdown", async (request, reply) => {
    const remote = request.ip;
    if (remote !== "127.0.0.1" && remote !== "::1" && remote !== "::ffff:127.0.0.1") {
      return reply.code(403).send({ error: "loopback_only" });
    }
    setImmediate(() => process.kill(process.pid, "SIGTERM"));
    return reply.code(202).send({ accepted: true });
  });

  app.get("/api/status", async () => ({
    bridgeState: client.currentState,
    eventSequence: events.currentSequence,
    pendingInteractions: threads.getPendingInteractions().length,
    codexBinary: config.codexBin
  }));

  app.get("/api/mcp-dialog/config", async (request) =>
    mcpDialogs.status(request.hostname || request.raw.socket.localAddress || null)
  );

  app.put("/api/mcp-dialog/config", async (request) => {
    const body = mcpDialogConfigSchema.parse(request.body);
    return mcpDialogs.configure(body, request.hostname || request.raw.socket.localAddress || null);
  });

  app.get("/api/mcp-dialog/requests", async () => ({ data: mcpDialogs.listPending() }));

  app.post("/api/mcp-dialog/requests/:requestId/respond", async (request, reply) => {
    const params = request.params as { requestId: string };
    const body = mcpDialogResponseSchema.parse(request.body);
    mcpDialogs.respond(params.requestId, body);
    return reply.code(202).send({ accepted: true });
  });

  registerLibreChatCompat(app, config, threads, events, vscodeInstances, files, workspace);

  app.get("/api/vscode/instances", async () => vscodeInstances.list());
  app.get("/api/vscode/online-conversations", async () => vscodeInstances.onlineConversations());

  app.post("/api/vscode/bind", async (request) => {
    const body = vscodeBindingSchema.parse(request.body);
    return vscodeInstances.bind(body.instanceId);
  });

  app.post("/api/vscode/command", async (request, reply) => {
    const body = vscodeCommandSchema.parse(request.body);
    const openPath = body.type === "openFile" ? workspace.absolutePath(body.rootId!, body.path!) : undefined;
    const command = vscodeInstances.enqueue(body.type, body.threadId, openPath);
    return reply.code(202).send({ accepted: true, command });
  });

  app.post("/api/vscode/instances/:instanceId/threads/:threadId/close", async (request, reply) => {
    const params = request.params as { instanceId: string; threadId: string };
    const instanceId = z.string().uuid().parse(params.instanceId);
    const threadId = z.string().min(1).max(200).parse(params.threadId);
    const command = vscodeInstances.enqueueFor(instanceId, "closeThread", threadId);
    return reply.code(202).send({ accepted: true, command });
  });

  app.post("/internal/vscode-companion/register", async (request) => {
    if (!isLoopback(request.ip)) throw new Error("Companion registration is loopback only");
    const registration = vscodeRegistrationSchema.parse(request.body);
    const instance = vscodeInstances.register(registration);
    if (config.codexProxyPath && !sameExecutablePath(registration.codexCliExecutable, config.codexProxyPath)) {
      vscodeInstances.configureCodexProxy(registration.instanceId, config.codexProxyPath);
    }
    threads.updateDesktopActivitiesForInstance(registration.instanceId, registration.openThreads.map((thread) => ({
      threadId: thread.threadId,
      running: thread.running,
      terminalStatus: thread.terminalStatus,
      ...(thread.activityUpdatedAt === null ? {} : { updatedAt: thread.activityUpdatedAt })
    })));
    return instance;
  });

  app.get("/internal/vscode-companion/:instanceId/commands", async (request) => {
    if (!isLoopback(request.ip)) throw new Error("Companion polling is loopback only");
    const { instanceId } = request.params as { instanceId: string };
    const query = request.query as Record<string, unknown>;
    const after = typeof query.after === "string" ? Number.parseInt(query.after, 10) : 0;
    const wait = typeof query.wait === "string" ? Number.parseInt(query.wait, 10) : 25_000;
    const data = await vscodeInstances.poll(
      z.string().uuid().parse(instanceId),
      Number.isFinite(after) ? Math.max(0, after) : 0,
      Number.isFinite(wait) ? Math.max(0, Math.min(wait, 25_000)) : 25_000
    );
    const instance = vscodeInstances.list().data.find((entry) => entry.instanceId === instanceId);
    return {
      data,
      activity: (instance?.openThreads ?? []).map((thread) => ({
        threadId: thread.threadId,
        ...threads.getActivity(thread.threadId)
      }))
    };
  });

  app.post("/internal/vscode-companion/:instanceId/threads/:threadId/interrupt", async (request, reply) => {
    if (!isLoopback(request.ip)) throw new Error("Companion interruption is loopback only");
    const params = request.params as { instanceId: string; threadId: string };
    const instanceId = z.string().uuid().parse(params.instanceId);
    const threadId = z.string().min(1).max(200).parse(params.threadId);
    const instance = vscodeInstances.list().data.find((entry) => entry.instanceId === instanceId && entry.online);
    if (!instance?.openThreads.some((thread) => thread.threadId === threadId)) {
      return reply.code(409).send({ error: "thread_not_open_in_instance" });
    }
    const body = interruptSchema.parse(request.body ?? {});
    return threads.interrupt(threadId, body.expectedTurnId);
  });

  app.post("/internal/vscode-companion/:instanceId/heartbeat", async (request) => {
    if (!isLoopback(request.ip)) throw new Error("Companion heartbeat is loopback only");
    const { instanceId } = request.params as { instanceId: string };
    vscodeInstances.heartbeat(z.string().uuid().parse(instanceId));
    return { ok: true };
  });

  app.post("/internal/vscode-companion/:instanceId/complete", async (request) => {
    if (!isLoopback(request.ip)) throw new Error("Companion completion is loopback only");
    const { instanceId } = request.params as { instanceId: string };
    const body = vscodeCompletionSchema.parse(request.body);
    vscodeInstances.complete(z.string().uuid().parse(instanceId), body.sequence);
    return { ok: true };
  });

  app.post("/internal/vscode-companion/:instanceId/offline", async (request) => {
    if (!isLoopback(request.ip)) throw new Error("Companion offline notification is loopback only");
    const { instanceId } = request.params as { instanceId: string };
    const parsedInstanceId = z.string().uuid().parse(instanceId);
    threads.clearDesktopActivitiesForInstance(parsedInstanceId);
    vscodeInstances.unregister(parsedInstanceId);
    return { ok: true };
  });

  app.get("/api/modes", async () => reads.run(() => threads.listModes()));

  app.get("/api/events/poll", async (request) => {
    const query = request.query as Record<string, unknown>;
    const since = Math.max(0, Number(query.since) || 0);
    const wait = Math.max(0, Math.min(Number(query.wait) || 25_000, 25_000));
    return events.poll(since, wait);
  });

  app.get("/api/workspace/roots", async () => ({ data: workspace.roots() }));
  app.get("/api/ftp/status", async (request) => ftp.status(request.raw.socket.localAddress));
  app.post("/api/ftp/start", async (request) => {
    const body = (request.body ?? {}) as { port?: number; username?: string; password?: string };
    const port = Number.isInteger(body.port) && body.port! > 0 && body.port! <= 65_535 ? body.port! : config.ftpPort;
    const credentials: { username?: string; password?: string } = {};
    if (typeof body.username === "string") credentials.username = body.username;
    if (typeof body.password === "string") credentials.password = body.password;
    return ftp.start(port, request.raw.socket.localAddress, credentials);
  });
  app.post("/api/ftp/stop", async () => ftp.stop());
  app.get("/api/workspace/entries", async (request) => {
    const query = request.query as Record<string, unknown>;
    return workspace.list(String(query.rootId ?? ""), typeof query.path === "string" ? query.path : "");
  });
  app.get("/api/workspace/file", async (request) => {
    const query = request.query as Record<string, unknown>;
    return workspace.readText(String(query.rootId ?? ""), String(query.path ?? ""));
  });
  app.get("/api/workspace/raw", async (request, reply) => {
    const query = request.query as Record<string, unknown>;
    const resource = workspace.raw(String(query.rootId ?? ""), String(query.path ?? ""));
    reply.type(resource.type);
    reply.header("Cache-Control", "no-cache");
    return reply.send(resource.stream);
  });

  app.get("/api/threads", async (request) => {
    const query = request.query as Record<string, unknown>;
    const result = await reads.run(() => threads.listThreads({
      cursor: typeof query.cursor === "string" && query.cursor ? query.cursor : null,
      limit: integerQuery(query.limit, 30, 100),
      search: typeof query.search === "string" && query.search.trim() ? query.search.trim() : null,
      archived: query.archived === "true",
      repair: query.repair === "true"
    }));
    return result;
  });

  app.get("/api/threads/:threadId/messages", async (request) => {
    const params = request.params as { threadId: string };
    const query = request.query as Record<string, unknown>;
    return reads.run(() => threads.listMessages({
      threadId: params.threadId,
      cursor: typeof query.cursor === "string" && query.cursor ? query.cursor : null,
      limit: integerQuery(query.limit, 20, 50)
    }));
  });

  app.post("/api/threads/:threadId/messages", async (request, reply) => {
    const params = request.params as { threadId: string };
    const body = sendMessageSchema.parse(request.body);
    const selectedFiles = body.fileIds.map((fileId) => files.get(fileId)).filter((file) => file !== null);
    const result = await threads.sendMessage({
      threadId: params.threadId,
      clientMessageId: body.clientMessageId,
      text: body.text,
      mode: body.mode,
      model: body.model ?? null,
      reasoningEffort: body.reasoningEffort ?? null,
      approvalMode: body.fullAccess ? "fullAccess" : "request",
      deliveryMode: body.deliveryMode,
      expectedTurnId: body.expectedTurnId ?? null,
      imagePaths: selectedFiles.filter((file) => file.type.startsWith("image/"))
        .map((file) => files.inputPath(file.fileId)),
      mentionedFiles: selectedFiles.filter((file) => !file.type.startsWith("image/"))
        .map((file) => ({ name: file.filename, path: files.inputPath(file.fileId) }))
    });
    return reply.code(result.status === "started" ? 201 : 202).send(result);
  });

  app.get("/api/threads/:threadId/activity", async (request) => {
    const params = request.params as { threadId: string };
    return threads.getActivity(params.threadId);
  });

  app.get("/api/threads/:threadId/retry", async (request) => {
    const params = request.params as { threadId: string };
    return { policy: threads.getRetryPolicy(params.threadId), status: threads.getActivity(params.threadId).retryStatus };
  });

  app.put("/api/threads/:threadId/retry", async (request) => {
    const params = request.params as { threadId: string };
    return { policy: threads.setRetryPolicy(params.threadId, retryPolicySchema.parse(request.body)) };
  });

  app.post("/api/threads/:threadId/retry/cancel", async (request) => {
    const params = request.params as { threadId: string };
    return threads.cancelRetry(params.threadId);
  });

  app.post("/api/threads/:threadId/interrupt", async (request) => {
    const params = request.params as { threadId: string };
    const body = interruptSchema.parse(request.body ?? {});
    return threads.interrupt(params.threadId, body.expectedTurnId);
  });

  app.get("/api/threads/:threadId/queue", async (request) => {
    const params = request.params as { threadId: string };
    return { data: threads.getQueue(params.threadId) };
  });

  app.delete("/api/threads/:threadId/queue/:clientMessageId", async (request, reply) => {
    const params = request.params as { threadId: string; clientMessageId: string };
    const result = threads.cancelQueuedMessage(params.threadId, params.clientMessageId);
    return reply.code(result.cancelled ? 200 : 404).send(result);
  });

  app.patch("/api/threads/:threadId/queue/:clientMessageId", async (request, reply) => {
    const params = request.params as { threadId: string; clientMessageId: string };
    const body = queueUpdateSchema.parse(request.body);
    const result = threads.updateQueuedMessage(params.threadId, params.clientMessageId, body.text);
    return reply.code(result.updated ? 200 : 404).send(result);
  });

  app.post("/api/threads/:threadId/queue/:clientMessageId/steer", async (request, reply) => {
    const params = request.params as { threadId: string; clientMessageId: string };
    const body = queueSteerSchema.parse(request.body);
    const result = await threads.steerQueuedMessage(
      params.threadId,
      params.clientMessageId,
      body.expectedTurnId
    );
    return reply.code(200).send(result);
  });

  app.put("/api/threads/:threadId/queue/order", async (request, reply) => {
    const params = request.params as { threadId: string };
    const body = queueReorderSchema.parse(request.body);
    const result = threads.reorderQueue(params.threadId, body.clientMessageIds);
    return reply.code(result.reordered ? 200 : 409).send(result);
  });

  app.post("/api/threads/:threadId/queue/pause", async (request) => {
    const params = request.params as { threadId: string };
    return threads.pauseQueue(params.threadId);
  });

  app.post("/api/threads/:threadId/queue/resume", async (request) => {
    const params = request.params as { threadId: string };
    return threads.resumeQueue(params.threadId);
  });

  app.get("/api/interactions", async () => ({
    data: threads.getPendingInteractions().map((interaction) => ({
      ...interaction,
      requestId: String(interaction.requestId)
    }))
  }));

  app.post("/api/interactions/:requestId/respond", async (request, reply) => {
    const params = request.params as { requestId: string };
    const body = interactionResponseSchema.parse(request.body);
    threads.respondToInteraction(params.requestId, body.result);
    return reply.code(202).send({ accepted: true });
  });

  app.post("/api/interactions/:requestId/action", async (request, reply) => {
    const params = request.params as { requestId: string };
    const body = interactionActionSchema.parse(request.body);
    threads.respondToInteractionAction(params.requestId, body.action);
    return reply.code(202).send({ accepted: true });
  });

  app.post("/api/interactions/:requestId/answer", async (request, reply) => {
    const params = request.params as { requestId: string };
    const body = interactionAnswersSchema.parse(request.body);
    threads.respondToUserInput(params.requestId, body.answers);
    return reply.code(202).send({ accepted: true });
  });

  app.get("/api/events", { websocket: true }, (socket, request) => {
    const query = request.query as Record<string, unknown>;
    const since = typeof query.since === "string" ? Number.parseInt(query.since, 10) : null;
    events.subscribe(socket, Number.isFinite(since) ? since : null);
  });

  app.get("/internal/vscode", { websocket: true }, (socket, request) => {
    const remote = request.ip;
    if (remote !== "127.0.0.1" && remote !== "::1" && remote !== "::ffff:127.0.0.1") {
      socket.close(1008, "Loopback only");
      return;
    }
    vscodeProxy.attach(socket);
  });

  app.addHook("onClose", async () => {
    if (ftp.status().running) await ftp.stop();
  });

  app.setErrorHandler((error, _request, reply) => {
    const message = error instanceof Error ? error.message : String(error);
    const status = error instanceof z.ZodError
      ? 400
      : message.includes("queue limit") || message.includes("queue is full")
        ? 429
        : message.includes("timed out")
          ? 504
          : message.includes("missing or already resolved")
            ? 409
            : 500;
    void reply.code(status).send({ error: message });
  });

  return app;
}

function isLoopback(address: string): boolean {
  return address === "127.0.0.1" || address === "::1" || address === "::ffff:127.0.0.1";
}

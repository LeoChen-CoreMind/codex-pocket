import type { ServerResponse } from "node:http";
import type { FastifyInstance } from "fastify";
import type { BridgeEvent, BridgeFileReference, ChatRecord, CollaborationModeName, ThreadSummary } from "../types.js";
import type { BridgeConfig } from "../config.js";
import { EventBus } from "./event-bus.js";
import { ThreadService } from "./thread-service.js";
import { VsCodeInstanceService } from "./vscode-instance-service.js";
import { liveOperationText, operationItemText } from "./operation-text.js";
import { FileStore } from "./file-store.js";
import { WorkspaceService, workspaceRootForPath, type WorkspaceRoot } from "./workspace-service.js";
import { WorkspaceSkillService, type SkillPayload } from "./workspace-skill-service.js";

interface CompatStream {
  threadId: string;
  text: string;
  userMessageId: string;
  parentMessageId: string;
  assistantText: string;
  assistantMessageId: string;
  model: string | null;
  mode: CollaborationModeName;
  clients: Set<ServerResponse>;
  finalPayload: Record<string, unknown> | null;
  thinkingParts: string[];
  thinkingByItem: Map<string, string>;
  itemPhases: Map<string, "commentary" | "final" | null>;
  toolParts: Array<Record<string, unknown>>;
  orderedParts: Array<Record<string, unknown>>;
  orderedItemIndex: Map<string, number>;
  completedOperationIds: Set<string>;
  imageFiles: BridgeFileReference[];
  imageTasks: Array<Promise<void>>;
  userFiles: BridgeFileReference[];
  createdAt: number;
  recovering: boolean;
  completionPending: boolean;
  finalizing: boolean;
}

function orderedAssistantContent(records: ChatRecord[]): {
  content: Array<Record<string, unknown>>;
  text: string;
  itemIndex: Map<string, number>;
} {
  const content: Array<Record<string, unknown>> = [];
  const itemIndex = new Map<string, number>();
  let text = "";
  let thinkingChars = 0;
  let toolChars = 0;
  let toolCount = 0;

  for (const record of records) {
    if (!record.text) continue;
    if (itemIndex.has(record.itemId)) continue;
    if (record.kind === "thinking" || (record.kind === "message" && record.phase === "commentary")) {
      const remaining = MAX_THINKING_CHARS - thinkingChars;
      if (remaining <= 0) continue;
      const think = cappedText(record.text, remaining);
      thinkingChars += think.length;
      itemIndex.set(record.itemId, content.push({ type: "think", think }) - 1);
      continue;
    }
    if (record.kind === "tool") {
      if (toolCount >= MAX_TOOL_PARTS || toolChars >= MAX_TOOL_TOTAL_CHARS) continue;
      const output = cappedText(record.text, Math.min(MAX_TOOL_OUTPUT_CHARS, MAX_TOOL_TOTAL_CHARS - toolChars));
      toolChars += output.length;
      toolCount++;
      itemIndex.set(record.itemId, content.push({
        type: "tool_call",
        tool_call: {
          id: `${stableRecordId(record)}-tool`,
          name: record.tool ?? "operation",
          args: {},
          output
        }
      }) - 1);
      continue;
    }
    if (record.kind === "message" && (record.phase === "final" || record.phase === null)) {
      const remaining = MAX_ASSISTANT_TEXT_CHARS - text.length;
      if (remaining <= 0) continue;
      const partText = cappedText(record.text, remaining);
      text += partText;
      itemIndex.set(record.itemId, content.push({ type: "text", text: partText }) - 1);
    }
  }
  return { content, text, itemIndex };
}

function ensureOrderedPart(
  stream: CompatStream,
  itemId: string,
  kind: "think" | "text" | "tool_call",
  toolName?: string
): number {
  const existing = stream.orderedItemIndex.get(itemId);
  if (existing !== undefined) {
    const part = stream.orderedParts[existing];
    if (part && part.type !== kind && part.type !== "tool_call") {
      stream.orderedParts[existing] = kind === "think"
        ? { type: "think", think: String(part.text ?? part.think ?? "") }
        : { type: "text", text: String(part.text ?? part.think ?? "") };
    }
    return existing;
  }
  const part = kind === "think"
    ? { type: "think", think: "" }
    : kind === "text"
      ? { type: "text", text: "" }
      : { type: "tool_call", tool_call: { id: itemId, name: toolName ?? "operation", args: {}, output: "" } };
  const index = stream.orderedParts.push(part) - 1;
  stream.orderedItemIndex.set(itemId, index);
  return index;
}

function setOrderedText(
  stream: CompatStream,
  itemId: string,
  kind: "think" | "text",
  value: string,
  append: boolean
): void {
  const index = ensureOrderedPart(stream, itemId, kind);
  const part = stream.orderedParts[index]!;
  const key = kind === "think" ? "think" : "text";
  const limit = kind === "think" ? MAX_THINKING_CHARS : MAX_ASSISTANT_TEXT_CHARS;
  const current = String(part[key] ?? "");
  stream.orderedParts[index] = { type: kind, [key]: cappedText(append ? current + value : value, limit) };
}

function setOrderedTool(
  stream: CompatStream,
  itemId: string,
  toolName: string,
  input: string,
  output: string | null
): void {
  const index = ensureOrderedPart(stream, itemId, "tool_call", toolName);
  stream.orderedParts[index] = {
    type: "tool_call",
    tool_call: { id: itemId, name: toolName, args: input ? { detail: input } : {}, output: output ?? "" }
  };
}

function richerOperationText(previous: unknown, next: string): string {
  const current = typeof previous === "string" ? previous : "";
  if (!current) return next;
  if (!next) return current;
  return next.length >= current.length ? next : current;
}

export function compatResumeFrame(orderedParts: Array<Record<string, unknown>>): Record<string, unknown> {
  return {
    sync: true,
    resumeState: { aggregatedContent: orderedParts },
    pendingEvents: []
  };
}

const MAX_ASSISTANT_TEXT_CHARS = 512 * 1024;
const MAX_THINKING_CHARS = 256 * 1024;
const MAX_TOOL_OUTPUT_CHARS = 128 * 1024;
const MAX_TOOL_TOTAL_CHARS = 384 * 1024;
const MAX_TOOL_PARTS = 128;
const TRUNCATION_MARKER = "\n\n[... content truncated by Codex Pocket ...]\n\n";

function cappedText(value: string, maxChars: number): string {
  if (value.length <= maxChars) return value;
  if (maxChars <= TRUNCATION_MARKER.length) return value.slice(0, Math.max(0, maxChars));
  const available = Math.max(0, maxChars - TRUNCATION_MARKER.length);
  const head = Math.ceil(available * 0.7);
  const tail = available - head;
  return `${value.slice(0, head)}${TRUNCATION_MARKER}${tail > 0 ? value.slice(-tail) : ""}`;
}

function appendCapped(current: string, delta: string, maxChars: number): string {
  return cappedText(current + delta, maxChars);
}

function mergeRecoveredText(history: string, live: string, maxChars: number): string {
  if (!history) return cappedText(live, maxChars);
  if (!live || history.endsWith(live)) return cappedText(history, maxChars);
  if (live.startsWith(history)) return cappedText(live, maxChars);
  return cappedText(history + live, maxChars);
}

function cappedToolParts(parts: Array<Record<string, unknown>>): Array<Record<string, unknown>> {
  let remaining = MAX_TOOL_TOTAL_CHARS;
  const result: Array<Record<string, unknown>> = [];
  for (const part of parts.slice(0, MAX_TOOL_PARTS)) {
    const toolCall = part.tool_call as Record<string, unknown> | undefined;
    if (!toolCall || typeof toolCall.output !== "string") {
      result.push(part);
      continue;
    }
    if (remaining <= 0) break;
    const output = cappedText(toolCall.output, Math.min(MAX_TOOL_OUTPUT_CHARS, remaining));
    remaining -= output.length;
    result.push({ ...part, tool_call: { ...toolCall, output } });
  }
  return result;
}

function iso(timestamp: number | null | undefined): string {
  return new Date(timestamp ?? Date.now()).toISOString();
}

function conversation(thread: ThreadSummary, instructions: string | null = null) {
  const project = thread.cwd.trim() ? workspaceRootForPath(thread.cwd) : null;
  return {
    conversationId: thread.id,
    title: thread.title,
    user: "codex-local",
    endpoint: "openAI",
    endpointType: "openAI",
    model: "Codex Default",
    system: instructions,
    tags: project ? [project.name] : [],
    chatProjectId: project?.id ?? null,
    isArchived: thread.archived,
    pinned: false,
    status: thread.status,
    isRunning: thread.status === "active" || thread.status === "running",
    createdAt: iso(thread.updatedAt),
    updatedAt: iso(thread.updatedAt)
  };
}

function wireFile(file: BridgeFileReference) {
  return {
    file_id: file.fileId,
    filename: file.filename,
    filepath: file.filepath,
    type: file.type,
    bytes: file.bytes,
    width: file.width,
    height: file.height,
    source: "local"
  };
}

function stableRecordId(record: ChatRecord): string {
  return /^item-\d+$/.test(record.itemId) ? `${record.turnId}-${record.itemId}` : record.itemId;
}

async function recordFiles(records: ChatRecord[], files: FileStore): Promise<Array<Record<string, unknown>>> {
  const result: Array<Record<string, unknown>> = [];
  const seen = new Set<string>();
  for (const path of records.flatMap((record) => record.imagePaths ?? [])) {
    const file = await files.importLocalImage(path);
    if (file && !seen.has(file.fileId)) {
      seen.add(file.fileId);
      result.push(wireFile(file));
    }
  }
  return result;
}

async function messages(records: ChatRecord[], files: FileStore) {
  let parentMessageId = "00000000-0000-0000-0000-000000000000";
  const result: Array<Record<string, unknown>> = [];
  const turns = new Map<string, ChatRecord[]>();
  for (const record of records) {
    const turn = turns.get(record.turnId) ?? [];
    turn.push(record);
    turns.set(record.turnId, turn);
  }

  for (const turnRecords of turns.values()) {
    const userRecords = turnRecords.filter((record) => record.role === "user");
    const assistantRecords = turnRecords.filter((record) => record.role === "assistant");
    if (userRecords.length > 0) {
      const anchor = userRecords[0]!;
      const messageId = stableRecordId(anchor);
      const attachedFiles = await recordFiles(userRecords, files);
      result.push({
        messageId,
        conversationId: anchor.threadId,
        parentMessageId,
        user: "codex-local",
        model: null,
        endpoint: "openAI",
        sender: "You",
        text: userRecords.map((record) => record.text).filter(Boolean).join("\n\n"),
        files: attachedFiles.length > 0 ? attachedFiles : undefined,
        isCreatedByUser: true,
        error: false,
        unfinished: userRecords.some((record) => record.state === "streaming"),
        createdAt: iso(anchor.timestamp),
        updatedAt: iso(anchor.timestamp)
      });
      parentMessageId = messageId;
    }

    if (assistantRecords.length > 0) {
      const finalRecords = assistantRecords.filter((record) =>
        record.kind === "message" && (record.phase === "final" || record.phase === null)
      );
      const ordered = orderedAssistantContent(assistantRecords);
      const text = ordered.text;
      const anchor = finalRecords.at(-1) ?? assistantRecords.at(-1)!;
      const messageId = finalRecords.length > 0 ? stableRecordId(anchor) : `${anchor.turnId}-assistant`;
      const attachedFiles = await recordFiles(assistantRecords, files);
      const content = ordered.content;
      result.push({
        messageId,
        conversationId: anchor.threadId,
        parentMessageId,
        user: null,
        model: "Codex",
        endpoint: "openAI",
        sender: "Codex",
        text,
        content: content.length > 0 ? content : undefined,
        files: attachedFiles.length > 0 ? attachedFiles : undefined,
        isCreatedByUser: false,
        error: false,
        unfinished: assistantRecords.some((record) => record.state === "streaming"),
        createdAt: iso(anchor.timestamp),
        updatedAt: iso(anchor.timestamp)
      });
      parentMessageId = messageId;
    }
  }
  return result;
}

async function allThreads(
  threads: ThreadService
): Promise<ThreadSummary[]> {
  const result: ThreadSummary[] = [];
  let cursor: string | null = null;
  for (let page = 0; page < 20; page++) {
    const response = await threads.listThreads({ cursor, limit: 100, search: null, archived: false, repair: false });
    result.push(...response.data);
    cursor = response.nextCursor;
    if (!cursor) break;
  }
  return result;
}

async function allMessages(threads: ThreadService, threadId: string): Promise<ChatRecord[]> {
  let cursor: string | null = null;
  let result: ChatRecord[] = [];
  for (let page = 0; page < 50; page++) {
    const response = await threads.listMessages({ threadId, cursor, limit: 50, includeOperations: true });
    result = [...response.data, ...result];
    cursor = response.nextCursor;
    if (!cursor) break;
  }
  return result;
}

function latestTurnIsFinished(records: ChatRecord[]): boolean {
  const latestTurnId = records.at(-1)?.turnId;
  if (!latestTurnId) return false;
  const latestTurn = records.filter((record) => record.turnId === latestTurnId);
  return latestTurn.length > 0 && latestTurn.every((record) => record.state !== "streaming");
}

function recoveredStream(threadId: string, records: ChatRecord[]): CompatStream {
  const latestTurnId = records.at(-1)?.turnId;
  const turnRecords = latestTurnId ? records.filter((record) => record.turnId === latestTurnId) : [];
  const userRecords = turnRecords.filter((record) => record.role === "user");
  const assistantRecords = turnRecords.filter((record) => record.role === "assistant");
  const finalRecords = assistantRecords.filter((record) =>
    record.kind === "message" && (record.phase === "final" || record.phase === null)
  );
  const assistantAnchor = finalRecords.at(-1) ?? assistantRecords.at(-1);
  const ordered = orderedAssistantContent(assistantRecords);
  const toolParts = ordered.content.filter((part) => part.type === "tool_call");

  return {
    threadId,
    text: userRecords.map((record) => record.text).filter(Boolean).join("\n\n"),
    userMessageId: userRecords[0] ? stableRecordId(userRecords[0]) : `recovered-user-${Date.now()}`,
    parentMessageId: "00000000-0000-0000-0000-000000000000",
    assistantText: ordered.text,
    assistantMessageId: assistantAnchor ? stableRecordId(assistantAnchor) : `recovered-assistant-${Date.now()}`,
    model: null,
    mode: "default",
    clients: new Set(),
    finalPayload: null,
    thinkingParts: ordered.content.filter((part) => part.type === "think").map((part) => String(part.think ?? "")),
    thinkingByItem: new Map(),
    itemPhases: new Map<string, "commentary" | "final" | null>(
      assistantRecords.map((record) => [record.itemId, record.phase] as const)
    ),
    toolParts,
    orderedParts: ordered.content,
    orderedItemIndex: ordered.itemIndex,
    completedOperationIds: new Set(
      assistantRecords
        .filter((record) =>
          (record.kind === "thinking" || record.kind === "tool") && record.state !== "streaming"
        )
        .map((record) => record.itemId)
    ),
    imageFiles: [],
    imageTasks: [],
    userFiles: [],
    createdAt: userRecords[0]?.timestamp ?? Date.now(),
    recovering: true,
    completionPending: false,
    finalizing: false
  };
}

function mergeRecoveredStream(target: CompatStream, recovered: CompatStream): void {
  if (!target.text) target.text = recovered.text;
  if (target.userMessageId.startsWith("recovered-user-")) target.userMessageId = recovered.userMessageId;
  if (target.assistantMessageId.startsWith("recovered-assistant-")) {
    target.assistantMessageId = recovered.assistantMessageId;
  }
  target.assistantText = mergeRecoveredText(recovered.assistantText, target.assistantText, MAX_ASSISTANT_TEXT_CHARS);
  const historyThinking = recovered.thinkingParts.join("\n\n");
  const liveThinking = [...target.thinkingParts, ...target.thinkingByItem.values()].join("\n\n");
  const mergedThinking = mergeRecoveredText(historyThinking, liveThinking, MAX_THINKING_CHARS);
  target.thinkingParts = mergedThinking ? [mergedThinking] : [];
  target.thinkingByItem.clear();
  const existingToolIds = new Set(target.toolParts.map((part) => {
    const call = part.tool_call as Record<string, unknown> | undefined;
    return typeof call?.id === "string" ? call.id : null;
  }));
  target.toolParts = [
    ...recovered.toolParts.filter((part) => {
      const call = part.tool_call as Record<string, unknown> | undefined;
      return typeof call?.id !== "string" || !existingToolIds.has(call.id);
    }),
    ...target.toolParts
  ];
  if (target.orderedParts.length === 0) {
    target.orderedParts = recovered.orderedParts;
    target.orderedItemIndex = new Map(recovered.orderedItemIndex);
  } else {
    const recoveredEntries = [...recovered.orderedItemIndex.entries()]
      .filter(([itemId]) => !target.orderedItemIndex.has(itemId))
      .sort((left, right) => left[1] - right[1]);
    if (recoveredEntries.length > 0) {
      const prefix = recoveredEntries.map(([, index]) => recovered.orderedParts[index]!);
      const shifted = new Map<string, number>();
      recoveredEntries.forEach(([itemId], index) => shifted.set(itemId, index));
      for (const [itemId, index] of target.orderedItemIndex) {
        shifted.set(itemId, prefix.length + index);
      }
      target.orderedParts = [...prefix, ...target.orderedParts];
      target.orderedItemIndex = shifted;
    }
  }
  for (const itemId of recovered.completedOperationIds) target.completedOperationIds.add(itemId);
  if (recovered.createdAt < target.createdAt) target.createdAt = recovered.createdAt;
}

async function threadPage(
  threads: ThreadService,
  input: {
    cursor: string | null;
    limit: number;
    search: string | null;
    archived: boolean;
    projectId: string | null;
  }
): Promise<{ data: ThreadSummary[]; nextCursor: string | null }> {
  if (!input.projectId) {
    const response = await threads.listThreads({
      cursor: input.cursor,
      limit: input.limit,
      search: input.search,
      archived: input.archived,
      repair: false
    });
    return { data: response.data, nextCursor: response.nextCursor };
  }

  const matching: ThreadSummary[] = [];
  let sourceCursor: string | null = null;
  for (let page = 0; page < 20; page++) {
    const response = await threads.listThreads({
      cursor: sourceCursor,
      limit: 100,
      search: input.search,
      archived: input.archived,
      repair: false
    });
    matching.push(...response.data.filter((thread) => {
      if (input.projectId === "unassigned") return !thread.cwd.trim();
      if (!thread.cwd.trim()) return false;
      return workspaceRootForPath(thread.cwd).id === input.projectId;
    }));
    sourceCursor = response.nextCursor;
    if (!sourceCursor) break;
  }
  const offsetMatch = /^project:(\d+)$/.exec(input.cursor ?? "");
  const offset = offsetMatch ? Number.parseInt(offsetMatch[1]!, 10) : 0;
  const data = matching.slice(offset, offset + input.limit);
  const nextOffset = offset + data.length;
  return {
    data,
    nextCursor: nextOffset < matching.length ? `project:${nextOffset}` : null
  };
}

function writeSse(response: ServerResponse, payload: unknown): void {
  if (response.destroyed || response.writableEnded) return;
  response.write(`data: ${JSON.stringify(payload)}\n\n`);
}

function parseModelOption(value: unknown): { model: string | null; mode: CollaborationModeName } {
  if (typeof value !== "string" || !value.trim()) return { model: null, mode: "default" };
  const match = /^\[(正常|计划|目标)\]\s+(.+)$/.exec(value.trim());
  if (match) {
    return {
      model: match[2]!,
      mode: match[1] === "计划" ? "plan" : match[1] === "目标" ? "agent" : "default"
    };
  }
  if (value.toLowerCase().includes("plan")) return { model: null, mode: "plan" };
  return { model: value, mode: "default" };
}

export function registerLibreChatCompat(
  app: FastifyInstance,
  config: BridgeConfig,
  threads: ThreadService,
  events: EventBus,
  vscodeInstances: VsCodeInstanceService,
  files: FileStore,
  workspace: WorkspaceService
): void {
  const streams = new Map<string, CompatStream>();
  const skills = new WorkspaceSkillService(workspace);

  app.get("/api/config", async () => ({
    appTitle: "Codex Pocket",
    emailLoginEnabled: true,
    registrationEnabled: false,
    socialLoginEnabled: false,
    emailEnabled: true,
    passwordResetEnabled: false,
    serverDomain: "codex-bridge.local",
    allowAccountDeletion: false,
    version: "0.8.7",
    interface: {
      endpointsMenu: true,
      modelSelect: true,
      parameters: false,
      presets: false,
      prompts: false,
      bookmarks: false,
      multiConvo: false,
      agents: false,
      skills: true
    }
  }));

  app.post("/api/auth/login", async (request, reply) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    const password = typeof body.password === "string" ? body.password.trim() : "";
    if (config.apiToken && password !== config.apiToken) {
      return reply.code(401).send({ message: "访问令牌不正确" });
    }
    const token = config.apiToken ?? "codex-local";
    reply.header("Set-Cookie", `refreshToken=${token}; Path=/; HttpOnly; SameSite=Strict`);
    return {
      token,
      user: {
        id: "codex-local",
        _id: "codex-local",
        name: "Codex",
        username: "codex",
        email: "codex@local",
        emailVerified: true,
        provider: "local",
        role: "USER",
        termsAccepted: true
      }
    };
  });

  app.post("/api/auth/refresh", async (request, reply) => {
    const token = config.apiToken ?? "codex-local";
    reply.header("Set-Cookie", `refreshToken=${token}; Path=/; HttpOnly; SameSite=Strict`);
    return { token };
  });

  app.post("/api/auth/logout", async () => ({ success: true }));
  app.get("/api/user", async () => ({
    id: "codex-local",
    _id: "codex-local",
    name: "Codex",
    username: "codex",
    email: "codex@local",
    emailVerified: true,
    provider: "local",
    role: "USER",
    termsAccepted: true
  }));

  app.get("/api/roles/:roleName", async (request) => {
    const { roleName } = request.params as { roleName: string };
    return {
      name: roleName,
      description: "Codex Pocket local workspace",
      permissions: {
        AGENTS: { USE: false, CREATE: false },
        BOOKMARKS: { USE: true, CREATE: true },
        SKILLS: { USE: true, CREATE: true, SHARE: false, SHARE_PUBLIC: false }
      }
    };
  });

  app.get("/api/user/settings/skills/active", async () => skills.states());
  app.post("/api/user/settings/skills/active", async (request) => {
    const body = (request.body ?? {}) as { skillStates?: Record<string, boolean> };
    return skills.setStates(body.skillStates ?? {});
  });

  app.get("/api/skills", async (request) => {
    const query = request.query as Record<string, unknown>;
    return skills.list({
      limit: Math.min(Math.max(Number(query.limit) || 100, 1), 100),
      search: typeof query.search === "string" && query.search.trim() ? query.search.trim() : null,
      cursor: typeof query.cursor === "string" && query.cursor ? query.cursor : null
    });
  });

  app.post("/api/skills/import", async (request, reply) => {
    const part = await request.file();
    if (!part) return reply.code(400).send({ error: "Missing skill import file" });
    return skills.import(part.filename, await part.toBuffer());
  });

  app.get("/api/skills/:skillId/export", async (request, reply) => {
    const { skillId } = request.params as { skillId: string };
    const archive = await skills.export(skillId);
    reply.type("application/zip");
    reply.header("Content-Disposition", `attachment; filename=\"${archive.filename}\"`);
    return reply.send(archive.bytes);
  });

  app.get("/api/skills/:skillId/files", async (request) => {
    const { skillId } = request.params as { skillId: string };
    return { files: await skills.listFiles(skillId) };
  });

  app.post("/api/skills/:skillId/files", async (request, reply) => {
    const { skillId } = request.params as { skillId: string };
    let relativePath = "";
    let upload: Buffer | null = null;
    for await (const part of request.parts()) {
      if (part.type === "file") {
        upload = await part.toBuffer();
        if (!relativePath) relativePath = part.filename;
      } else if (part.fieldname === "relativePath") {
        relativePath = String(part.value ?? "");
      }
    }
    if (!upload || !relativePath) return reply.code(400).send({ error: "Missing skill file or relativePath" });
    return skills.writeFile(skillId, relativePath, upload);
  });

  app.get("/api/skills/:skillId/files/:relativePath", async (request) => {
    const { skillId, relativePath } = request.params as { skillId: string; relativePath: string };
    return skills.readFile(skillId, relativePath);
  });

  app.delete("/api/skills/:skillId/files/:relativePath", async (request) => {
    const { skillId, relativePath } = request.params as { skillId: string; relativePath: string };
    await skills.deleteFile(skillId, relativePath);
    return { skillId, relativePath, deleted: true };
  });

  app.get("/api/skills/:skillId", async (request) => {
    const { skillId } = request.params as { skillId: string };
    return skills.get(skillId);
  });

  app.post("/api/skills", async (request) => skills.create((request.body ?? {}) as SkillPayload));

  app.patch("/api/skills/:skillId", async (request, reply) => {
    const { skillId } = request.params as { skillId: string };
    const result = await skills.update(skillId, (request.body ?? {}) as SkillPayload);
    if (result.conflict) return reply.code(409).send({ error: "skill_version_conflict", current: result.current });
    return result.skill;
  });

  app.delete("/api/skills/:skillId", async (request) => {
    const { skillId } = request.params as { skillId: string };
    await skills.delete(skillId);
    return { id: skillId, deleted: true };
  });

  app.get("/api/endpoints", async () => ({
    openAI: {
      type: "openAI",
      order: 0,
      name: "Codex",
      modelDisplayLabel: "Codex",
      userProvide: false,
      capabilities: []
    }
  }));
  app.get("/api/models", async () => {
    const models = await threads.listModels();
    const ordered = [...models].sort((left, right) => Number(right.isDefault) - Number(left.isDefault));
    return {
      openAI: ordered.map((model) => model.model)
    };
  });
  app.get("/api/agents", async () => ({ object: "list", data: [], has_more: false }));
  app.get("/api/categories", async () => []);
  app.get("/api/tags", async () => []);
  app.get("/api/presets", async () => []);

  app.get("/api/projects", async () => {
    const all = await allThreads(threads);
    const grouped = new Map<string, { root: WorkspaceRoot; threads: ThreadSummary[] }>();
    for (const thread of all) {
      if (!thread.cwd.trim()) continue;
      const root = workspaceRootForPath(thread.cwd);
      const group = grouped.get(root.id) ?? { root, threads: [] };
      group.threads.push(thread);
      grouped.set(root.id, group);
    }
    return {
      projects: [...grouped.values()].map(({ root, threads: projectThreads }) => {
        const updatedAt = projectThreads.reduce((latest, thread) => Math.max(latest, thread.updatedAt), 0);
        return {
          _id: root.id,
          name: root.name,
          description: root.path,
          user: "codex-local",
          conversationCount: projectThreads.length,
          lastConversationAt: updatedAt ? iso(updatedAt) : null,
          lastConversationId: projectThreads[0]?.id ?? null,
          createdAt: null,
          updatedAt: updatedAt ? iso(updatedAt) : null
        };
      }),
      nextCursor: null
    };
  });

  app.get("/api/files/config", async () => files.config());
  app.get("/api/files", async () => files.list().map(wireFile));
  app.post("/api/files", async (request, reply) => {
    const part = await request.file();
    if (!part) return reply.code(400).send({ message: "缺少上传文件" });
    return wireFile(await files.saveUpload(part));
  });
  app.get("/api/files/:fileId", async (request, reply) => {
    const { fileId } = request.params as { fileId: string };
    const stored = files.get(fileId);
    if (!stored) return reply.code(404).send({ message: "文件不存在" });
    const resource = files.stream(fileId);
    reply.type(resource.file.type);
    reply.header("Content-Length", resource.file.bytes);
    reply.header("Cache-Control", "private, max-age=86400");
    return reply.send(resource.stream);
  });
  app.delete("/api/files", async (request) => {
    const body = (request.body ?? {}) as { files?: Array<{ file_id?: string }> };
    await files.delete((body.files ?? []).map((file) => file.file_id).filter((id): id is string => Boolean(id)));
    return { success: true };
  });

  app.post("/api/workspace/import", async (request) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    if (typeof body.rootId !== "string" || typeof body.path !== "string") {
      throw new Error("rootId and path are required");
    }
    const absolute = workspace.absolutePath(body.rootId, body.path);
    return wireFile(await files.importLocalFile(absolute));
  });

  app.get("/api/convos", async (request) => {
    const query = request.query as Record<string, unknown>;
    const limit = Math.min(Math.max(Number(query.limit) || 25, 1), 100);
    const response = await threadPage(threads, {
      projectId: typeof query.projectId === "string" && query.projectId ? query.projectId : null,
      cursor: typeof query.cursor === "string" && query.cursor ? query.cursor : null,
      limit,
      search: typeof query.search === "string" && query.search.trim() ? query.search.trim() : null,
      archived: query.isArchived === "true"
    });
    return {
      conversations: response.data.map((thread) => conversation(thread, threads.getThreadInstructions(thread.id))),
      nextCursor: response.nextCursor
    };
  });

  app.get("/api/convos/:threadId", async (request, reply) => {
    const { threadId } = request.params as { threadId: string };
    const thread = (await allThreads(threads)).find((item) => item.id === threadId);
    return thread
      ? conversation(thread, threads.getThreadInstructions(thread.id))
      : reply.code(404).send({ message: "Conversation not found" });
  });

  app.get("/api/convos/:threadId/instructions", async (request) => {
    const { threadId } = request.params as { threadId: string };
    return { instructions: threads.getThreadInstructions(threadId) };
  });

  app.put("/api/convos/:threadId/instructions", async (request, reply) => {
    const { threadId } = request.params as { threadId: string };
    const body = (request.body ?? {}) as Record<string, unknown>;
    if (body.instructions !== null && body.instructions !== undefined && typeof body.instructions !== "string") {
      return reply.code(400).send({ message: "instructions must be a string or null" });
    }
    const raw = typeof body.instructions === "string" ? body.instructions : null;
    if (raw && raw.length > 32_000) {
      return reply.code(400).send({ message: "instructions are too long" });
    }
    return { instructions: threads.setThreadInstructions(threadId, raw) };
  });

  app.get("/api/messages/:threadId", async (request) => {
    const { threadId } = request.params as { threadId: string };
    const page = await threads.listMessages({ threadId, cursor: null, limit: 20, includeOperations: true });
    return messages(page.data, files);
  });

  app.get("/api/messages/:threadId/page", async (request) => {
    const { threadId } = request.params as { threadId: string };
    const query = request.query as Record<string, unknown>;
    const cursor = typeof query.cursor === "string" && query.cursor ? query.cursor : null;
    const limit = Math.min(Math.max(Number(query.limit) || 20, 1), 50);
    const page = await threads.listMessages({ threadId, cursor, limit, includeOperations: true });
    return {
      messages: await messages(page.data, files),
      nextCursor: page.nextCursor,
      backwardsCursor: page.backwardsCursor
    };
  });

  app.post("/api/agents/chat/:endpoint", async (request, reply) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    let threadId = typeof body.conversationId === "string" && body.conversationId
      ? body.conversationId
      : null;
    const text = typeof body.text === "string" ? body.text.trim() : "";
    const requestedFiles = Array.isArray(body.files) ? body.files as Array<Record<string, unknown>> : [];
    const userFiles = requestedFiles
      .map((file) => typeof file.file_id === "string" ? files.get(file.file_id) : null)
      .filter((file): file is NonNullable<typeof file> => file !== null);
    if (!text && userFiles.length === 0) {
      return reply.code(400).send({ message: "消息和图片不能同时为空" });
    }
    if (!threadId) {
      threadId = await threads.createThread(vscodeInstances.workspaceForNewThread());
    }
    if (typeof body.system === "string") {
      threads.setThreadInstructions(threadId, body.system);
    }
    vscodeInstances.enqueue("openThread", threadId);
    const userMessageId = typeof body.messageId === "string" && body.messageId ? body.messageId : `mobile-${Date.now()}`;
    const parentMessageId = typeof body.parentMessageId === "string" ? body.parentMessageId : "00000000-0000-0000-0000-000000000000";
    const selection = parseModelOption(body.model);
    const codexMode = body.codex_mode;
    const explicitMode: CollaborationModeName | null = codexMode === "plan" || body.mode === "plan"
      ? "plan"
      : codexMode === "agent" || body.mode === "agent" || body.effort === "ultra"
        ? "agent"
        : codexMode === "default" ? "default" : null;
    const mode = explicitMode ?? selection.mode;
    const reasoningEffort = typeof body.reasoning_effort === "string" &&
      !["plan", "agent"].includes(body.reasoning_effort)
      ? body.reasoning_effort
      : null;
    const legacyFullAccess = body.codex_full_access === true;
    const requestedApprovalMode = body.codex_approval_mode;
    const approvalMode = requestedApprovalMode === "auto" || requestedApprovalMode === "fullAccess" || requestedApprovalMode === "request"
      ? requestedApprovalMode
      : legacyFullAccess ? "fullAccess" : "request";
    streams.set(threadId, {
      threadId,
      text,
      userMessageId,
      parentMessageId,
      assistantText: "",
      assistantMessageId: `assistant-${Date.now()}`,
      model: selection.model,
      mode,
      clients: new Set(),
      finalPayload: null,
      thinkingParts: [],
      thinkingByItem: new Map(),
      itemPhases: new Map(),
      toolParts: [],
      orderedParts: [],
      orderedItemIndex: new Map(),
      completedOperationIds: new Set(),
      imageFiles: [],
      imageTasks: [],
      userFiles,
      createdAt: Date.now(),
      recovering: false,
      completionPending: false,
      finalizing: false
    });
    const deliveryMode = body.deliveryMode === "steer" || body.delivery_mode === "steer" ? "steer" : "queue";
    const expectedTurnId = typeof body.expectedTurnId === "string"
      ? body.expectedTurnId
      : typeof body.expected_turn_id === "string" ? body.expected_turn_id : null;
    await threads.sendMessage({
      threadId,
      clientMessageId: userMessageId,
      text,
      mode,
      model: selection.model,
      reasoningEffort,
      approvalMode,
      deliveryMode,
      expectedTurnId,
      imagePaths: userFiles.filter((file) => file.type.startsWith("image/"))
        .map((file) => files.inputPath(file.fileId)),
      mentionedFiles: userFiles.filter((file) => !file.type.startsWith("image/"))
        .map((file) => ({ name: file.filename, path: files.inputPath(file.fileId) }))
    });
    return { conversationId: threadId };
  });

  app.get("/api/agents/chat/stream/:threadId", async (request, reply) => {
    const { threadId } = request.params as { threadId: string };
    let stream = streams.get(threadId);
    if (!stream) {
      // A navigation hand-off or bridge restart can outlive the in-memory compatibility stream.
      // Register a recovery target before loading history so events emitted during the read are
      // still captured. Completed threads are finalized below; active threads remain attached.
      stream = recoveredStream(threadId, []);
      streams.set(threadId, stream);
      try {
        const history = await allMessages(threads, threadId);
        mergeRecoveredStream(stream, recoveredStream(threadId, history));
        stream.recovering = false;
        // A bridge restart clears ThreadService's in-memory activeTurnId. History is the
        // durable source of truth here: never finalize a still-running turn into a
        // tool-only assistant message just because the runtime map was rebuilt.
        if (stream.completionPending || (!threads.isThreadActive(threadId) && latestTurnIsFinished(history))) {
          await finalizeStream(threadId, stream);
        }
      } catch (error) {
        if (streams.get(threadId) === stream) streams.delete(threadId);
        throw error;
      }
    }
    reply.hijack();
    const response = reply.raw;
    response.writeHead(200, {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no"
    });
    response.write(": connected\n\n");
    if (stream.finalPayload) {
      writeSse(response, stream.finalPayload);
      response.end();
      return;
    }
    stream.clients.add(response);
    const heartbeat = setInterval(() => {
      if (!response.destroyed && !response.writableEnded) response.write(": keep-alive\n\n");
    }, 15_000);
    heartbeat.unref();
    const query = request.query as Record<string, unknown>;
    const isResume = query.resume === true || query.resume === "true";
    if (isResume) {
      writeSse(response, compatResumeFrame(stream.orderedParts));
    } else for (const part of stream.orderedParts) {
      if (part.type === "think" && typeof part.think === "string" && part.think) {
        writeSse(response, { type: "thinking", text: part.think });
      } else if (part.type === "tool_call") {
        const toolCall = part.tool_call as Record<string, unknown> | undefined;
        if (typeof toolCall?.id !== "string") continue;
        writeSse(response, {
          type: "tool_call_start",
          toolCallId: toolCall.id,
          toolName: typeof toolCall.name === "string" ? toolCall.name : "operation",
          input: typeof toolCall.args === "object" ? JSON.stringify(toolCall.args) : ""
        });
        if (typeof toolCall.output === "string" && toolCall.output) {
          writeSse(response, { type: "tool_call_complete", toolCallId: toolCall.id, output: toolCall.output });
        }
      } else if (part.type === "text" && typeof part.text === "string" && part.text) {
        writeSse(response, {
          event: "on_message_delta",
          data: { id: stream.assistantMessageId, delta: { content: [{ type: "text", text: part.text }] } }
        });
      }
    }
    request.raw.on("close", () => {
      clearInterval(heartbeat);
      stream?.clients.delete(response);
    });
  });

  app.post("/api/agents/chat/abort", async (request) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    const threadId = typeof body.abortKey === "string" && body.abortKey ? body.abortKey : [...streams.keys()].at(-1);
    if (!threadId) return { success: true, aborted: false };
    const expectedTurnId = typeof body.expectedTurnId === "string"
      ? body.expectedTurnId
      : typeof body.expected_turn_id === "string" ? body.expected_turn_id : null;
    const result = await threads.interrupt(threadId, expectedTurnId);
    return { success: true, aborted: result.interrupted };
  });

  app.get("/api/agents/chat/status/:threadId", async (request) => {
    const { threadId } = request.params as { threadId: string };
    const activity = threads.getActivity(threadId);
    return { isActive: activity.active, ...activity };
  });

  const finalizeStream = async (threadId: string, stream: CompatStream): Promise<void> => {
    if (stream.finalizing || stream.finalPayload) return;
    stream.finalizing = true;
    await Promise.allSettled(stream.imageTasks);
    const thread = {
      id: threadId,
      title: stream.text.slice(0, 80) || "图片对话",
      projectName: "Codex",
      cwd: "",
      source: "vscode",
      updatedAt: Date.now(),
      archived: false,
      status: "idle"
    } satisfies ThreadSummary;
    const requestMessage = {
      messageId: stream.userMessageId,
      conversationId: threadId,
      parentMessageId: stream.parentMessageId,
      endpoint: "openAI",
      sender: "You",
      text: stream.text,
      files: stream.userFiles.length > 0 ? stream.userFiles.map(wireFile) : undefined,
      isCreatedByUser: true,
      createdAt: iso(stream.createdAt)
    };
    const content = stream.orderedParts;
    const responseMessage = {
      messageId: stream.assistantMessageId,
      conversationId: threadId,
      parentMessageId: stream.userMessageId,
      model: stream.model ?? "Codex",
      endpoint: "openAI",
      sender: "Codex",
      text: cappedText(stream.assistantText, MAX_ASSISTANT_TEXT_CHARS),
      content: content.length > 0 ? content : undefined,
      files: stream.imageFiles.length > 0 ? stream.imageFiles.map(wireFile) : undefined,
      isCreatedByUser: false,
      createdAt: iso(Date.now())
    };
    stream.finalPayload = {
      final: true,
      conversation: conversation(thread, threads.getThreadInstructions(thread.id)),
      requestMessage,
      responseMessage
    };
    for (const client of stream.clients) {
      writeSse(client, stream.finalPayload);
      client.end();
    }
    stream.clients.clear();
    setTimeout(() => {
      // Do not let an earlier turn's delayed cleanup delete a newer stream for the same thread.
      if (streams.get(threadId) === stream) streams.delete(threadId);
    }, 5 * 60_000).unref();
  };

  const onEvent = (event: BridgeEvent) => {
    const threadId = event.threadId;
    if (!threadId) return;
    const stream = streams.get(threadId);
    if (!stream) return;
    if (event.type === "message.started" || event.type === "message.completed") {
      const record = event.payload as ChatRecord;
      if (record.role === "user") return;
      stream.itemPhases.set(record.itemId, record.phase);
      if (record.phase === "commentary") {
        stream.thinkingByItem.set(record.itemId, record.text);
        ensureOrderedPart(stream, record.itemId, "think");
        if (event.type === "message.completed" && record.text) {
          setOrderedText(stream, record.itemId, "think", record.text, false);
        }
      } else {
        ensureOrderedPart(stream, record.itemId, "text");
        if (event.type === "message.completed" && record.text) {
          setOrderedText(stream, record.itemId, "text", record.text, false);
        }
      }
      return;
    }
    if (event.type === "message.delta") {
      const payload = event.payload as { itemId?: string; delta?: string; phase?: "commentary" | "final" | null };
      if (!payload.delta) return;
      stream.assistantMessageId = payload.itemId || stream.assistantMessageId;
      if (payload.itemId && (payload.phase === "commentary" || stream.itemPhases.get(payload.itemId) === "commentary")) {
        const current = stream.thinkingByItem.get(payload.itemId) ?? "";
        stream.thinkingByItem.set(payload.itemId, appendCapped(current, payload.delta, MAX_THINKING_CHARS));
        setOrderedText(stream, payload.itemId, "think", payload.delta, true);
        for (const client of stream.clients) writeSse(client, { type: "thinking", text: payload.delta });
        return;
      }
      stream.assistantText = appendCapped(stream.assistantText, payload.delta, MAX_ASSISTANT_TEXT_CHARS);
      setOrderedText(stream, payload.itemId ?? stream.assistantMessageId, "text", payload.delta, true);
      const frame = {
        event: "on_message_delta",
        data: { id: stream.assistantMessageId, delta: { content: [{ type: "text", text: payload.delta }] } }
      };
      for (const client of stream.clients) writeSse(client, frame);
      return;
    }
    if (event.type === "item/fileChange/patchUpdated") {
      const payload = event.payload as { itemId?: string; changes?: unknown[] };
      if (!payload.itemId || !payload.changes) return;
      const output = cappedText(
        liveOperationText(operationItemText({ type: "fileChange", changes: payload.changes })),
        MAX_TOOL_OUTPUT_CHARS
      );
      const existingIndex = stream.orderedItemIndex.get(payload.itemId);
      const existingPart = existingIndex === undefined ? undefined : stream.orderedParts[existingIndex];
      const existingCall = existingPart?.tool_call as Record<string, unknown> | undefined;
      const existingArgs = existingCall?.args as Record<string, unknown> | undefined;
      const mergedOutput = richerOperationText(existingCall?.output, output);
      setOrderedTool(
        stream,
        payload.itemId,
        String(existingCall?.name ?? "fileChange"),
        String(existingArgs?.detail ?? ""),
        mergedOutput
      );
      for (const client of stream.clients) writeSse(client, {
        type: "tool_call_complete",
        toolCallId: payload.itemId,
        output: mergedOutput
      });
      return;
    }
    if (event.type === "operation.started" || event.type === "operation.completed") {
      const payload = event.payload as { item?: Record<string, unknown> };
      const item = payload.item;
      if (!item || typeof item.id !== "string" || typeof item.type !== "string") return;
      const detail = cappedText(liveOperationText(operationItemText(item)), MAX_TOOL_OUTPUT_CHARS);
      if (item.type === "reasoning" || item.type === "plan") {
        ensureOrderedPart(stream, item.id, "think");
        if (event.type === "operation.completed" && detail && !stream.completedOperationIds.has(item.id)) {
          stream.completedOperationIds.add(item.id);
          stream.thinkingParts.push(detail);
          setOrderedText(stream, item.id, "think", detail, false);
          for (const client of stream.clients) writeSse(client, { type: "thinking", text: `${detail}\n\n` });
        }
        return;
      }
      if (event.type === "operation.started") {
        setOrderedTool(stream, item.id, item.type, detail, null);
        for (const client of stream.clients) writeSse(client, {
          type: "tool_call_start",
          toolCallId: item.id,
          toolName: item.type,
          input: detail
        });
      } else {
        const existingIndex = stream.orderedItemIndex.get(item.id);
        const existingPart = existingIndex === undefined ? undefined : stream.orderedParts[existingIndex];
        const existingCall = existingPart?.tool_call as Record<string, unknown> | undefined;
        const existingArgs = existingCall?.args as Record<string, unknown> | undefined;
        const input = richerOperationText(existingArgs?.detail, detail);
        const output = richerOperationText(existingCall?.output, detail);
        setOrderedTool(stream, item.id, item.type, input, output);
        const firstCompletion = !stream.completedOperationIds.has(item.id);
        stream.completedOperationIds.add(item.id);
        const toolPart = {
          type: "tool_call",
          tool_call: { id: item.id, name: item.type, args: {}, output }
        };
        const toolPartIndex = stream.toolParts.findIndex((part) => {
          const call = part.tool_call as Record<string, unknown> | undefined;
          return call?.id === item.id;
        });
        if (toolPartIndex >= 0) {
          stream.toolParts[toolPartIndex] = toolPart;
        } else if (stream.toolParts.length < MAX_TOOL_PARTS) {
          stream.toolParts.push(toolPart);
        }
        if (firstCompletion) for (const client of stream.clients) writeSse(client, {
          type: "tool_call_complete",
          toolCallId: item.id,
          output
        });
        const imagePath = item.type === "imageView" && typeof item.path === "string"
          ? item.path
          : item.type === "imageGeneration" && typeof item.savedPath === "string"
            ? item.savedPath
            : null;
        if (imagePath) {
          const imageTask = files.importLocalImage(imagePath).then((file) => {
            if (!file || stream.imageFiles.some((current) => current.fileId === file.fileId)) return;
            stream.imageFiles.push(file);
            const frame = { event: "attachment", data: wireFile(file) };
            for (const client of stream.clients) writeSse(client, frame);
          });
          stream.imageTasks.push(imageTask);
        }
      }
      return;
    }
    if (event.type !== "turn.completed") return;
    if (stream.recovering) {
      stream.completionPending = true;
      return;
    }
    void finalizeStream(threadId, stream);
  };
  events.on("event", onEvent);
  app.addHook("onClose", async () => events.off("event", onEvent));
}

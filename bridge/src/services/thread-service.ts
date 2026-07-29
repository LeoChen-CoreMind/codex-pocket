import { basename } from "node:path";
import { dirname } from "node:path";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import type { BridgeConfig } from "../config.js";
import { CodexClient } from "../app-server/codex-client.js";
import type {
  ChatRecord,
  CollaborationModeName,
  JsonRpcId,
  JsonRpcRequest,
  PendingInteraction,
  ThreadSummary
} from "../types.js";
import { EventBus } from "./event-bus.js";
import { operationItemText } from "./operation-text.js";

interface RawItem {
  type: string;
  id: string;
  text?: string;
  phase?: string | null;
  content?: Array<{ type: string; text?: string }>;
  [key: string]: unknown;
}

interface RawTurn {
  id: string;
  items: RawItem[];
  status: string;
  startedAt: number | null;
  completedAt: number | null;
}

interface RawThread {
  id: string;
  name: string | null;
  preview: string;
  cwd: string;
  source: unknown;
  updatedAt: number;
  status: { type: string };
  turns?: RawTurn[];
}

interface ResumeResult {
  thread: RawThread;
  model: string;
  reasoningEffort: string | null;
  initialTurnsPage?: { data?: RawTurn[] } | null;
}

export interface QueuedMessage {
  clientMessageId: string;
  text: string;
  mode: CollaborationModeName;
  model: string | null;
  imagePaths: string[];
  mentionedFiles: Array<{ name: string; path: string }>;
  reasoningEffort: string | null;
  approvalMode: ApprovalMode;
  enqueuedAt: number;
}

export type DeliveryMode = "queue" | "steer";

export interface ThreadActivity {
  active: boolean;
  turnId: string | null;
  source: "pocket" | "desktop" | null;
  steerable: boolean;
  queuePaused: boolean;
  queue: QueuedMessage[];
  retryPolicy: RetryPolicy;
  retryStatus: RetryStatus | null;
}

export interface RetryPolicy {
  enabled: boolean;
  maxRetries: number;
  untilSuccess: boolean;
  delaySeconds: number;
  retryPrompt: string;
}

export interface RetryStatus {
  state: "failed" | "scheduled" | "retrying" | "succeeded" | "exhausted" | "cancelled";
  turnId: string;
  turnStatus: string;
  reason: string;
  retryCount: number;
  scheduledAt: number | null;
}

function userInputText(message: Pick<QueuedMessage, "text" | "mentionedFiles">): string {
  if (message.mentionedFiles.length === 0) return message.text;
  const files = message.mentionedFiles
    .map((file) => `## ${file.name}: ${file.path}`)
    .join("\n\n");
  return `# Files mentioned by the user:\n\n${files}\n\n## My request for Codex:\n${message.text}`;
}

export type ApprovalMode = "request" | "auto" | "fullAccess";

interface RawModel {
  id: string;
  model: string;
  displayName: string;
  hidden: boolean;
  isDefault: boolean;
  inputModalities: string[];
}

interface RuntimeThread {
  loaded: boolean;
  activeTurnId: string | null;
  model: string | null;
  reasoningEffort: string | null;
  queue: QueuedMessage[];
  queuePaused: boolean;
  starting: Promise<void> | null;
  cwd: string | null;
  retryStatus: RetryStatus | null;
  retryTimer: NodeJS.Timeout | null;
  retrySuppressed: boolean;
}

interface DesktopActivity {
  active: boolean;
  updatedAt: number;
}

interface PlanConfirmation {
  threadId: string;
  turnId: string;
  sourceMessage: QueuedMessage;
}

const HUMAN_SOURCES = ["cli", "vscode", "appServer", "unknown"];
const MAX_THREAD_INSTRUCTIONS_CHARS = 32_000;
const MAX_RETRY_PROMPT_CHARS = 4_000;
const DEFAULT_RETRY_PROMPT = "刚才的任务异常中断。请先检查当前会话和工作区状态，从中断处继续，避免重复已经完成的操作。";
const DEFAULT_RETRY_POLICY: RetryPolicy = {
  enabled: false,
  maxRetries: 3,
  untilSuccess: false,
  delaySeconds: 5,
  retryPrompt: DEFAULT_RETRY_PROMPT
};

export function shouldRetry(policy: RetryPolicy, retryCount: number, suppressed: boolean): boolean {
  return policy.enabled && !suppressed && (policy.untilSuccess || retryCount < policy.maxRetries);
}

export class ThreadService {
  private readonly runtimes = new Map<string, RuntimeThread>();
  private readonly pendingInteractions = new Map<string, PendingInteraction>();
  private readonly clientMessages = new Map<string, number>();
  private readonly messagePhases = new Map<string, "commentary" | "final" | null>();
  private readonly fileChangesByItem = new Map<string, unknown[]>();
  private readonly turnMessages = new Map<string, QueuedMessage>();
  private readonly completedPlanTurns = new Set<string>();
  private readonly completedTurns = new Set<string>();
  private readonly steeringQueuedMessages = new Set<string>();
  private readonly planConfirmations = new Map<string, PlanConfirmation>();
  private readonly threadInstructions = new Map<string, string>();
  private readonly threadRetryPolicies = new Map<string, RetryPolicy>();
  private readonly manualInterruptTurns = new Set<string>();
  private readonly desktopActivities = new Map<string, Map<string, DesktopActivity>>();

  constructor(
    private readonly client: CodexClient,
    private readonly events: EventBus,
    private readonly config: BridgeConfig
  ) {
    this.loadThreadInstructions();
    this.loadThreadRetryPolicies();
    this.loadThreadQueues();
    client.on("notification", (method, params) => this.handleNotification(method, params));
    client.on("serverRequest", (request) => this.handleServerRequest(request));
    client.on("state", (state, detail) => events.publish("bridge.state", null, { state, detail }));
  }

  getThreadInstructions(threadId: string): string | null {
    return this.threadInstructions.get(threadId) ?? null;
  }

  setThreadInstructions(threadId: string, instructions: string | null): string | null {
    const normalized = instructions?.trim().slice(0, MAX_THREAD_INSTRUCTIONS_CHARS) || null;
    if (normalized) this.threadInstructions.set(threadId, normalized);
    else this.threadInstructions.delete(threadId);
    this.persistThreadInstructions();
    this.events.publish("thread.instructions.updated", threadId, { instructions: normalized });
    return normalized;
  }

  getRetryPolicy(threadId: string): RetryPolicy {
    return { ...(this.threadRetryPolicies.get(threadId) ?? DEFAULT_RETRY_POLICY) };
  }

  setRetryPolicy(threadId: string, policy: RetryPolicy): RetryPolicy {
    const normalized: RetryPolicy = {
      enabled: Boolean(policy.enabled),
      maxRetries: Math.max(1, Math.min(20, Math.trunc(policy.maxRetries || 3))),
      untilSuccess: Boolean(policy.untilSuccess),
      delaySeconds: Math.max(1, Math.min(300, Math.trunc(policy.delaySeconds || 5))),
      retryPrompt: policy.retryPrompt.trim().slice(0, MAX_RETRY_PROMPT_CHARS) || DEFAULT_RETRY_PROMPT
    };
    this.threadRetryPolicies.set(threadId, normalized);
    this.persistThreadRetryPolicies();
    if (!normalized.enabled) this.cancelRetry(threadId);
    else this.runtime(threadId).retrySuppressed = false;
    this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
    return normalized;
  }

  cancelRetry(threadId: string): ThreadActivity {
    const runtime = this.runtime(threadId);
    if (runtime.retryTimer) clearTimeout(runtime.retryTimer);
    runtime.retryTimer = null;
    runtime.retrySuppressed = true;
    const queuedRetryIds = runtime.queue
      .filter((message) => message.clientMessageId.startsWith("retry-"))
      .map((message) => message.clientMessageId);
    if (queuedRetryIds.length > 0) {
      const queuedRetryIdSet = new Set(queuedRetryIds);
      runtime.queue = runtime.queue.filter((message) => !queuedRetryIdSet.has(message.clientMessageId));
      for (const clientMessageId of queuedRetryIds) this.clientMessages.delete(clientMessageId);
      if (runtime.queue.length === 0) runtime.queuePaused = false;
      this.publishQueue(threadId, runtime);
    }
    if (runtime.retryStatus && ["failed", "scheduled", "retrying"].includes(runtime.retryStatus.state)) {
      runtime.retryStatus = { ...runtime.retryStatus, state: "cancelled", scheduledAt: null };
    }
    this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
    return this.getActivity(threadId);
  }

  async listThreads(input: {
    cursor: string | null;
    limit: number;
    search: string | null;
    archived: boolean;
    repair: boolean;
  }): Promise<{ data: ThreadSummary[]; nextCursor: string | null; backwardsCursor: string | null }> {
    const result = await this.client.request<{
      data: RawThread[];
      nextCursor: string | null;
      backwardsCursor: string | null;
    }>("thread/list", {
      cursor: input.cursor,
      limit: input.limit,
      sortKey: "updated_at",
      sortDirection: "desc",
      sourceKinds: HUMAN_SOURCES,
      archived: input.archived,
      searchTerm: input.search,
      useStateDbOnly: !input.repair
    }, this.config.historyTimeoutMs);

    return {
      data: result.data.map((thread) => this.toSummary(thread, input.archived)),
      nextCursor: result.nextCursor,
      backwardsCursor: result.backwardsCursor
    };
  }

  async listMessages(input: {
    threadId: string;
    cursor: string | null;
    limit: number;
    includeOperations?: boolean;
  }): Promise<{ data: ChatRecord[]; nextCursor: string | null; backwardsCursor: string | null }> {
    await this.ensureResumed(input.threadId);
    const result = await this.client.request<{
      data: RawTurn[];
      nextCursor: string | null;
      backwardsCursor: string | null;
    }>("thread/turns/list", {
      threadId: input.threadId,
      cursor: input.cursor,
      limit: input.limit,
      sortDirection: "desc",
      itemsView: "full"
    }, this.config.historyTimeoutMs);

    const records = [...result.data]
      .reverse()
      .flatMap((turn) => this.normalizeTurn(input.threadId, turn, input.includeOperations ?? false));
    return { data: records, nextCursor: result.nextCursor, backwardsCursor: result.backwardsCursor };
  }

  async listModes(): Promise<unknown> {
    return this.client.request("collaborationMode/list", {}, this.config.requestTimeoutMs);
  }

  async listModels(): Promise<RawModel[]> {
    const models: RawModel[] = [];
    let cursor: string | null = null;
    for (let page = 0; page < 10; page++) {
      const result: { data: RawModel[]; nextCursor: string | null } = await this.client.request(
        "model/list",
        { cursor, limit: 100, includeHidden: false },
        this.config.requestTimeoutMs
      );
      models.push(...result.data.filter((model) => !model.hidden));
      cursor = result.nextCursor;
      if (!cursor) break;
    }
    return models;
  }

  async sendMessage(input: {
    threadId: string;
    clientMessageId: string;
    text: string;
    mode: CollaborationModeName;
    model?: string | null;
    imagePaths?: string[];
    mentionedFiles?: Array<{ name: string; path: string }>;
    reasoningEffort?: string | null;
    approvalMode?: ApprovalMode;
    deliveryMode?: DeliveryMode;
    expectedTurnId?: string | null;
  }): Promise<{
    status: "started" | "queued" | "steered" | "duplicate";
    position?: number;
    turnId?: string;
    fallbackReason?: "desktop_active" | "turn_changed" | "not_steerable";
  }> {
    this.pruneClientMessageIds();
    if (this.clientMessages.has(input.clientMessageId)) return { status: "duplicate" };
    this.clientMessages.set(input.clientMessageId, Date.now());

    const runtime = await this.ensureResumed(input.threadId);
    const message: QueuedMessage = {
      ...input,
      model: input.model ?? null,
      imagePaths: input.imagePaths ?? [],
      mentionedFiles: input.mentionedFiles ?? [],
      reasoningEffort: input.reasoningEffort ?? null,
      approvalMode: input.approvalMode ?? "request",
      enqueuedAt: Date.now()
    };

    const pocketActive = this.isPocketTurn(runtime);
    const desktopActive = this.isDesktopThreadActive(input.threadId);
    if (input.deliveryMode === "steer" && runtime.activeTurnId && pocketActive) {
      if (!input.expectedTurnId || input.expectedTurnId !== runtime.activeTurnId) {
        const queued = this.enqueueMessage(input.threadId, runtime, message);
        return { ...queued, fallbackReason: "turn_changed" };
      }
      try {
        const result = await this.client.request<{ turnId: string }>("turn/steer", {
          threadId: input.threadId,
          expectedTurnId: input.expectedTurnId,
          clientUserMessageId: input.clientMessageId,
          input: this.turnInput(message)
        });
        this.events.publish("message.steered", input.threadId, {
          turnId: result.turnId,
          clientMessageId: input.clientMessageId
        });
        return { status: "steered", turnId: result.turnId };
      } catch (error) {
        const detail = error instanceof Error ? error.message : String(error);
        if (!/steer|active turn|expected turn|turn.*match/i.test(detail)) {
          this.clientMessages.delete(input.clientMessageId);
          throw error;
        }
        const queued = this.enqueueMessage(input.threadId, runtime, message);
        return { ...queued, fallbackReason: "not_steerable" };
      }
    }
    if (runtime.activeTurnId || runtime.starting || desktopActive) {
      const queued = this.enqueueMessage(input.threadId, runtime, message);
      return input.deliveryMode === "steer" && (desktopActive || Boolean(runtime.activeTurnId && !pocketActive))
        ? { ...queued, fallbackReason: "desktop_active" }
        : queued;
    }

    this.resetRetryChain(runtime);
    runtime.starting = this.startTurn(input.threadId, runtime, message).finally(() => {
      runtime.starting = null;
    });
    try {
      await runtime.starting;
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      if (this.scheduleRetry(
        input.threadId,
        `start-${Date.now()}`,
        "start_failed",
        reason,
        message,
        runtime,
        message.text
      )) {
        return { status: "queued", position: 1 };
      }
      throw error;
    }
    return { status: "started" };
  }

  async interrupt(threadId: string, expectedTurnId?: string | null): Promise<{
    interrupted: boolean;
    stale?: boolean;
    source?: "pocket" | "desktop" | null;
  }> {
    const runtime = await this.ensureResumed(threadId);
    if (!runtime.activeTurnId) {
      return { interrupted: false, source: this.isDesktopThreadActive(threadId) ? "desktop" : null };
    }
    if (!this.isPocketTurn(runtime)) {
      return { interrupted: false, source: "desktop" };
    }
    if (!expectedTurnId || expectedTurnId !== runtime.activeTurnId) {
      return { interrupted: false, stale: true, source: "pocket" };
    }
    this.manualInterruptTurns.add(String(runtime.activeTurnId));
    runtime.queuePaused = runtime.queue.length > 0;
    this.publishQueue(threadId, runtime);
    try {
      await this.client.request("turn/interrupt", { threadId, turnId: runtime.activeTurnId });
    } catch (error) {
      this.manualInterruptTurns.delete(String(runtime.activeTurnId));
      throw error;
    }
    return { interrupted: true, source: "pocket" };
  }

  getQueue(threadId: string): QueuedMessage[] {
    return [...this.runtime(threadId).queue];
  }

  isThreadActive(threadId: string): boolean {
    return this.getActivity(threadId).active;
  }

  getActivity(threadId: string): ThreadActivity {
    const runtime = this.runtime(threadId);
    const pocketActive = this.isPocketTurn(runtime);
    const foreignTurnActive = Boolean(runtime.activeTurnId && !pocketActive);
    const desktopActive = this.isDesktopThreadActive(threadId);
    return {
      active: pocketActive || foreignTurnActive || desktopActive,
      turnId: runtime.activeTurnId,
      source: pocketActive ? "pocket" : foreignTurnActive || desktopActive ? "desktop" : null,
      steerable: Boolean(runtime.activeTurnId && pocketActive),
      queuePaused: runtime.queuePaused,
      queue: [...runtime.queue],
      retryPolicy: this.getRetryPolicy(threadId),
      retryStatus: runtime.retryStatus ? { ...runtime.retryStatus } : null
    };
  }

  cancelQueuedMessage(threadId: string, clientMessageId: string): { cancelled: boolean } {
    const runtime = this.runtimes.get(threadId);
    if (!runtime) return { cancelled: false };
    const index = runtime.queue.findIndex((message) => message.clientMessageId === clientMessageId);
    if (index < 0) return { cancelled: false };
    runtime.queue.splice(index, 1);
    this.clientMessages.delete(clientMessageId);
    if (runtime.queue.length === 0) runtime.queuePaused = false;
    this.publishQueue(threadId, runtime);
    return { cancelled: true };
  }

  updateQueuedMessage(threadId: string, clientMessageId: string, text: string): { updated: boolean } {
    const runtime = this.runtime(threadId);
    const message = runtime.queue.find((entry) => entry.clientMessageId === clientMessageId);
    if (!message) return { updated: false };
    message.text = text.trim();
    this.publishQueue(threadId, runtime);
    return { updated: true };
  }

  async steerQueuedMessage(
    threadId: string,
    clientMessageId: string,
    expectedTurnId: string
  ): Promise<{ steered: boolean; reason?: "message_missing" | "turn_changed" | "not_steerable"; turnId?: string }> {
    const runtime = await this.ensureResumed(threadId);
    const index = runtime.queue.findIndex((message) => message.clientMessageId === clientMessageId);
    if (index < 0) return { steered: false, reason: "message_missing" };
    if (!runtime.activeTurnId || runtime.activeTurnId !== expectedTurnId || !this.isPocketTurn(runtime)) {
      return { steered: false, reason: "turn_changed" };
    }
    const steeringKey = `${threadId}:${clientMessageId}`;
    if (this.steeringQueuedMessages.has(steeringKey)) return { steered: false, reason: "not_steerable" };
    this.steeringQueuedMessages.add(steeringKey);
    const message = runtime.queue[index]!;
    try {
      const result = await this.client.request<{ turnId: string }>("turn/steer", {
        threadId,
        expectedTurnId,
        clientUserMessageId: message.clientMessageId,
        input: this.turnInput(message)
      });
      // Remove only after app-server confirms the exact active turn accepted the steer.
      runtime.queue.splice(index, 1);
      if (runtime.queue.length === 0) runtime.queuePaused = false;
      this.publishQueue(threadId, runtime);
      this.events.publish("message.steered", threadId, {
        turnId: result.turnId,
        clientMessageId: message.clientMessageId,
        fromQueue: true
      });
      return { steered: true, turnId: result.turnId };
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      if (/steer|active turn|expected turn|turn.*match/i.test(detail)) {
        return { steered: false, reason: "not_steerable" };
      }
      throw error;
    } finally {
      this.steeringQueuedMessages.delete(steeringKey);
    }
  }

  reorderQueue(threadId: string, clientMessageIds: string[]): { reordered: boolean } {
    const runtime = this.runtime(threadId);
    if (clientMessageIds.length !== runtime.queue.length) return { reordered: false };
    const byId = new Map(runtime.queue.map((message) => [message.clientMessageId, message]));
    if (new Set(clientMessageIds).size !== runtime.queue.length || clientMessageIds.some((id) => !byId.has(id))) {
      return { reordered: false };
    }
    runtime.queue = clientMessageIds.map((id) => byId.get(id)!);
    this.publishQueue(threadId, runtime);
    return { reordered: true };
  }

  pauseQueue(threadId: string): ThreadActivity {
    const runtime = this.runtime(threadId);
    runtime.queuePaused = runtime.queue.length > 0;
    this.publishQueue(threadId, runtime);
    return this.getActivity(threadId);
  }

  resumeQueue(threadId: string): ThreadActivity {
    const runtime = this.runtime(threadId);
    runtime.queuePaused = false;
    this.publishQueue(threadId, runtime);
    if (!this.isThreadActive(threadId)) void this.flushQueue(threadId, runtime);
    return this.getActivity(threadId);
  }

  updateDesktopActivitiesForInstance(
    instanceId: string,
    threads: Array<{ threadId: string; running: boolean; terminalStatus?: "completed" | "aborted" | null; updatedAt?: number }>
  ): void {
    const incoming = new Map(threads.map((thread) => [thread.threadId, thread]));
    const affected = new Set<string>();
    for (const [threadId, instances] of this.desktopActivities) {
      if (instances.delete(instanceId)) affected.add(threadId);
      if (instances.size === 0) this.desktopActivities.delete(threadId);
    }
    for (const thread of threads) {
      let instances = this.desktopActivities.get(thread.threadId);
      if (!instances) {
        instances = new Map();
        this.desktopActivities.set(thread.threadId, instances);
      }
      instances.set(instanceId, {
        active: thread.running,
        updatedAt: thread.updatedAt ?? Date.now()
      });
      affected.add(thread.threadId);
      if (!thread.running && thread.terminalStatus === "aborted") {
        const runtime = this.runtime(thread.threadId);
        if (runtime.activeTurnId && this.isPocketTurn(runtime)) {
          this.manualInterruptTurns.add(String(runtime.activeTurnId));
        }
        runtime.queuePaused = runtime.queue.length > 0;
        this.publishQueue(thread.threadId, runtime);
      }
    }
    for (const threadId of affected) {
      const activity = this.getActivity(threadId);
      this.events.publish("thread.activity.updated", threadId, activity);
      const terminal = incoming.get(threadId)?.terminalStatus;
      if (!activity.active && terminal === "completed" && !activity.queuePaused) {
        void this.flushQueue(threadId, this.runtime(threadId));
      }
    }
  }

  clearDesktopActivitiesForInstance(instanceId: string): void {
    this.updateDesktopActivitiesForInstance(instanceId, []);
  }

  private enqueueMessage(
    threadId: string,
    runtime: RuntimeThread,
    message: QueuedMessage
  ): { status: "queued"; position: number } {
    if (runtime.queue.length >= this.config.queueLimitPerThread) {
      this.clientMessages.delete(message.clientMessageId);
      throw new Error(`Thread queue limit reached: ${this.config.queueLimitPerThread}`);
    }
    runtime.queue.push(message);
    this.publishQueue(threadId, runtime);
    return { status: "queued", position: runtime.queue.length };
  }

  private publishQueue(threadId: string, runtime: RuntimeThread): void {
    this.persistThreadQueues();
    this.events.publish("queue.updated", threadId, {
      queue: runtime.queue,
      paused: runtime.queuePaused
    });
    this.events.publish("thread.activity.updated", threadId, this.getActivity(threadId));
  }

  private isDesktopThreadActive(threadId: string): boolean {
    return [...(this.desktopActivities.get(threadId)?.values() ?? [])].some((activity) => activity.active);
  }

  private isPocketTurn(runtime: RuntimeThread): boolean {
    return Boolean(
      runtime.starting ||
      (runtime.activeTurnId && this.turnMessages.has(runtime.activeTurnId))
    );
  }

  private turnInput(message: QueuedMessage): Array<Record<string, unknown>> {
    return [
      { type: "text", text: userInputText(message), text_elements: [] },
      ...message.imagePaths.map((path) => ({ type: "localImage", path })),
      ...message.mentionedFiles.map((file) => ({ type: "mention", name: file.name, path: file.path }))
    ];
  }

  getPendingInteractions(): PendingInteraction[] {
    return [...this.pendingInteractions.values()];
  }

  respondToInteraction(requestId: string, result: unknown): void {
    if (!this.tryRespondToInteraction(requestId, { result }, "bridge")) {
      throw new Error("Interaction is missing or already resolved");
    }
  }

  respondToInteractionAction(
    requestId: string,
    action: "accept" | "acceptForSession" | "decline"
  ): void {
    const interaction = this.pendingInteractions.get(requestId);
    if (!interaction) throw new Error("Interaction is missing or already resolved");

    let result: unknown;
    if (interaction.method === "item/commandExecution/requestApproval" ||
      interaction.method === "item/fileChange/requestApproval") {
      result = { decision: action };
    } else if (interaction.method === "item/permissions/requestApproval") {
      const params = (interaction.params ?? {}) as Record<string, unknown>;
      result = {
        permissions: action === "decline" ? {} : (params.permissions ?? {}),
        scope: action === "acceptForSession" ? "session" : "turn"
      };
    } else {
      throw new Error(`Unsupported mobile interaction method: ${interaction.method}`);
    }

    this.respondToInteraction(requestId, result);
  }

  respondToUserInput(requestId: string, answers: Record<string, string[]>): void {
    const interaction = this.pendingInteractions.get(requestId);
    if (!interaction) throw new Error("Interaction is missing or already resolved");
    const planConfirmation = this.planConfirmations.get(requestId);
    if (planConfirmation) {
      this.pendingInteractions.delete(requestId);
      this.planConfirmations.delete(requestId);
      this.events.publish("interaction.resolved", planConfirmation.threadId, {
        requestId,
        source: "bridge",
        result: { answers }
      });
      const answer = answers.planDecision?.find((value) => value.trim().length > 0)?.trim();
      if (!answer) return;
      const implement = answer === "是，实施此计划";
      const source = planConfirmation.sourceMessage;
      void this.sendMessage({
        threadId: planConfirmation.threadId,
        clientMessageId: `plan-confirm-${planConfirmation.turnId}`,
        text: implement ? "请实施上面的计划。" : answer === "否，继续调整计划"
          ? "请继续调整上面的计划，暂不实施。"
          : answer,
        mode: implement ? "default" : "plan",
        model: source.model,
        reasoningEffort: source.reasoningEffort,
        approvalMode: source.approvalMode
      }).catch((error) => {
        this.events.publish("queue.failed", planConfirmation.threadId, {
          clientMessageId: `plan-confirm-${planConfirmation.turnId}`,
          error: error instanceof Error ? error.message : String(error)
        });
      });
      return;
    }
    if (interaction.method !== "item/tool/requestUserInput") {
      throw new Error(`Unsupported user input interaction method: ${interaction.method}`);
    }
    const params = (interaction.params ?? {}) as { questions?: Array<{ id?: unknown }> };
    const questionIds = new Set(
      (params.questions ?? [])
        .map((question) => question.id)
        .filter((id): id is string => typeof id === "string" && id.length > 0)
    );
    const normalized = Object.fromEntries(
      [...questionIds].map((id) => [id, { answers: answers[id] ?? [] }])
    );
    this.respondToInteraction(requestId, { answers: normalized });
  }

  tryRespondToInteraction(
    requestId: string,
    response: { result?: unknown; error?: { code: number; message: string; data?: unknown } },
    source: "bridge" | "vscode"
  ): boolean {
    const interaction = this.pendingInteractions.get(requestId);
    if (!interaction) return false;
    this.pendingInteractions.delete(requestId);
    if (response.error) this.client.respondError(interaction.requestId, response.error);
    else this.client.respond(interaction.requestId, response.result);
    this.events.publish("interaction.resolved", interaction.threadId, { requestId, ...response, source });
    return true;
  }

  private async ensureResumed(threadId: string): Promise<RuntimeThread> {
    let runtime = this.runtimes.get(threadId);
    if (!runtime) {
      runtime = {
        loaded: false,
        activeTurnId: null,
        model: null,
        reasoningEffort: null,
        queue: [],
        queuePaused: false,
        starting: null,
        cwd: null,
        retryStatus: null,
        retryTimer: null,
        retrySuppressed: false
      };
      this.runtimes.set(threadId, runtime);
    }
    if (runtime.loaded) return runtime;

    const result = await this.client.request<ResumeResult>("thread/resume", {
      threadId,
      excludeTurns: true,
      initialTurnsPage: { limit: 1, sortDirection: "desc", itemsView: "notLoaded" }
    }, this.config.historyTimeoutMs);
    runtime.loaded = true;
    runtime.cwd = result.thread.cwd;
    runtime.model = result.model;
    runtime.reasoningEffort = result.reasoningEffort;
    if (result.thread.status.type === "active") {
      const latest = result.initialTurnsPage?.data?.[0];
      runtime.activeTurnId = latest?.id ?? null;
    }
    return runtime;
  }

  async getThreadCwd(threadId: string): Promise<string> {
    const runtime = await this.ensureResumed(threadId);
    if (!runtime.cwd) throw new Error("The thread has no workspace path");
    return runtime.cwd;
  }

  async createThread(cwd: string): Promise<string> {
    const result = await this.client.request<ResumeResult>("thread/start", { cwd }, this.config.historyTimeoutMs);
    const runtime = this.runtime(result.thread.id);
    runtime.loaded = true;
    runtime.cwd = result.thread.cwd;
    runtime.model = result.model;
    runtime.reasoningEffort = result.reasoningEffort;
    return result.thread.id;
  }

  private async startTurn(threadId: string, runtime: RuntimeThread, message: QueuedMessage): Promise<void> {
    const params: Record<string, unknown> = {
      threadId,
      clientUserMessageId: message.clientMessageId,
      input: this.turnInput(message)
    };
    if (message.model) params.model = message.model;
    if (message.approvalMode === "fullAccess") {
      params.sandboxPolicy = { type: "dangerFullAccess" };
      params.approvalPolicy = "never";
    } else {
      params.sandboxPolicy = {
        type: "workspaceWrite",
        writableRoots: runtime.cwd ? [runtime.cwd] : [],
        networkAccess: false,
        excludeTmpdirEnvVar: false,
        excludeSlashTmp: false
      };
      params.approvalPolicy = message.approvalMode === "auto" ? "untrusted" : "on-request";
    }
    // Collaboration mode is explicit on every turn. Omitting it after a plan turn makes the
    // app-server inherit plan for the thread, so the mobile UI appears unable to switch back.
    const effectiveReasoningEffort = message.mode === "agent"
      ? "ultra"
      : message.reasoningEffort ?? runtime.reasoningEffort;
    params.collaborationMode = {
      mode: message.mode === "plan" ? "plan" : "default",
      settings: {
        model: message.model ?? runtime.model ?? "",
        reasoning_effort: effectiveReasoningEffort,
        developer_instructions: this.getThreadInstructions(threadId)
      }
    };
    if (message.mode !== "plan" && effectiveReasoningEffort) {
      params.effort = effectiveReasoningEffort;
    }
    const result = await this.client.request<{ turn: { id: string } }>("turn/start", params);
    runtime.activeTurnId = result.turn.id;
    this.turnMessages.set(result.turn.id, message);
    this.events.publish("turn.started", threadId, {
      turnId: result.turn.id,
      clientMessageId: message.clientMessageId,
      mode: message.mode,
      model: message.model,
      reasoningEffort: message.reasoningEffort,
      approvalMode: message.approvalMode,
      imageCount: message.imagePaths.length
    });
    this.events.publish("thread.activity.updated", threadId, this.getActivity(threadId));
  }

  private handleNotification(method: string, params: unknown): void {
    const value = (params ?? {}) as Record<string, any>;
    const threadId = typeof value.threadId === "string" ? value.threadId : null;

    if (method === "turn/started" && threadId) {
      const turnId = value.turn?.id ?? value.turnId ?? null;
      this.runtime(threadId).activeTurnId = turnId;
      this.events.publish("turn.started", threadId, { ...value, turnId });
      this.events.publish("thread.activity.updated", threadId, this.getActivity(threadId));
    } else if (method === "turn/completed" && threadId) {
      const runtime = this.runtime(threadId);
      const turnId = value.turn?.id ?? value.turnId ?? runtime.activeTurnId;
      const completionKey = turnId ? `${threadId}:${String(turnId)}` : null;
      if (completionKey && this.completedTurns.has(completionKey)) return;
      if (completionKey) {
        this.completedTurns.add(completionKey);
        if (this.completedTurns.size > 10_000) {
          const oldest = this.completedTurns.values().next().value;
          if (oldest) this.completedTurns.delete(oldest);
        }
      }
      const status = String(value.turn?.status ?? value.status ?? "completed");
      const sourceMessage = turnId ? this.turnMessages.get(String(turnId)) : undefined;
      const manuallyInterrupted = turnId ? this.manualInterruptTurns.delete(String(turnId)) : false;
      const failure = status === "completed" ? null : {
        status,
        reason: this.failureReason(value, status, manuallyInterrupted),
        manuallyInterrupted
      };
      this.events.publish("turn.completed", threadId, { ...value, turnId, failure });
      if (turnId && this.completedPlanTurns.delete(String(turnId))) {
        this.createPlanConfirmation(threadId, String(turnId), runtime);
      }
      if (turnId) this.turnMessages.delete(String(turnId));
      const completesCurrentTurn = runtime.activeTurnId === null || turnId === null ||
        String(turnId) === String(runtime.activeTurnId);
      if (completesCurrentTurn) {
        runtime.activeTurnId = null;
        const retryScheduled = status !== "completed" && !manuallyInterrupted && sourceMessage
          ? this.scheduleRetry(threadId, String(turnId ?? ""), status, failure!.reason, sourceMessage, runtime)
          : false;
        if (status === "completed" && runtime.retryStatus?.retryCount) {
          runtime.retryStatus = {
            ...runtime.retryStatus,
            state: "succeeded",
            turnId: String(turnId ?? runtime.retryStatus.turnId),
            turnStatus: status,
            scheduledAt: null
          };
          this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
        } else if (status !== "completed" && !retryScheduled && (!sourceMessage || manuallyInterrupted)) {
          runtime.retryStatus = {
            state: manuallyInterrupted ? "cancelled" : "failed",
            turnId: String(turnId ?? ""),
            turnStatus: status,
            reason: failure!.reason,
            retryCount: runtime.retryStatus?.retryCount ?? 0,
            scheduledAt: null
          };
          this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
        } else if (status !== "completed" && !retryScheduled && runtime.queue.length > 0) {
          runtime.queuePaused = true;
        }
        for (const key of this.messagePhases.keys()) {
          if (key.startsWith(`${threadId}:`)) this.messagePhases.delete(key);
        }
        for (const key of this.fileChangesByItem.keys()) {
          if (key.startsWith(`${threadId}:`)) this.fileChangesByItem.delete(key);
        }
        this.publishQueue(threadId, runtime);
        if (!runtime.queuePaused && !retryScheduled) void this.flushQueue(threadId, runtime);
      }
    } else if (method === "item/agentMessage/delta" && threadId) {
      this.events.publish("message.delta", threadId, {
        turnId: value.turnId,
        itemId: value.itemId,
        delta: value.delta,
        phase: this.messagePhases.get(`${threadId}:${String(value.itemId ?? "")}`) ?? null
      });
    } else if ((method === "item/started" || method === "item/completed") && threadId) {
      const item = value.item as RawItem | undefined;
      if (item?.type === "userMessage" || item?.type === "agentMessage") {
        const record = this.normalizeItem(threadId, String(value.turnId ?? ""), item, null, method === "item/completed");
        if (record) {
          this.messagePhases.set(`${threadId}:${item.id}`, record.phase);
          this.events.publish(method === "item/completed" ? "message.completed" : "message.started", threadId, record);
        }
      } else if (item) {
        if (method === "item/completed" && item.type === "plan" && value.turnId) {
          this.completedPlanTurns.add(String(value.turnId));
        }
        this.events.publish(method === "item/completed" ? "operation.completed" : "operation.started", threadId, {
          turnId: value.turnId,
          item,
          itemId: item.id,
          tool: item.type,
          detail: operationItemText(item as Record<string, unknown>)
        });
      }
    } else if (method === "serverRequest/resolved") {
      const requestId = String(value.requestId ?? value.id ?? "");
      if (requestId) this.pendingInteractions.delete(requestId);
      this.events.publish("interaction.resolved", threadId, value);
    } else if (threadId && (
      method === "turn/plan/updated" ||
      method === "item/plan/delta" ||
      method.startsWith("item/fileChange/") ||
      method.startsWith("item/commandExecution/") ||
      method === "thread/status/changed"
    )) {
      if (method === "item/fileChange/patchUpdated" && typeof value.itemId === "string" && Array.isArray(value.changes)) {
        this.fileChangesByItem.set(`${threadId}:${value.itemId}`, value.changes);
        for (const [requestId, interaction] of this.pendingInteractions) {
          if (
            interaction.method !== "item/fileChange/requestApproval" ||
            interaction.threadId !== threadId ||
            interaction.itemId !== value.itemId
          ) continue;
          const currentParams = (interaction.params ?? {}) as Record<string, unknown>;
          const updatedInteraction = {
            ...interaction,
            params: { ...currentParams, changes: value.changes }
          };
          this.pendingInteractions.set(requestId, updatedInteraction);
          this.events.publish("interaction.requested", threadId, updatedInteraction);
        }
      }
      this.events.publish(method, threadId, method === "item/fileChange/patchUpdated"
        ? {
            ...value,
            detail: operationItemText({ type: "fileChange", changes: value.changes })
          }
        : value);
    }
  }

  private handleServerRequest(request: JsonRpcRequest): void {
    const params = (request.params ?? {}) as Record<string, unknown>;
    const threadId = typeof params.threadId === "string" ? params.threadId : null;
    const itemId = typeof params.itemId === "string" ? params.itemId : null;
    const trackedChanges = request.method === "item/fileChange/requestApproval" && threadId && itemId
      ? this.fileChangesByItem.get(`${threadId}:${itemId}`)
      : undefined;
    const interactionParams = trackedChanges && (!Array.isArray(params.changes) || params.changes.length === 0)
      ? { ...params, changes: trackedChanges }
      : request.params;
    const interaction: PendingInteraction = {
      requestId: request.id,
      method: request.method,
      threadId,
      turnId: typeof params.turnId === "string" ? params.turnId : null,
      itemId,
      params: interactionParams,
      createdAt: Date.now()
    };
    this.pendingInteractions.set(String(request.id), interaction);
    this.events.publish("interaction.requested", interaction.threadId, interaction);
  }

  private createPlanConfirmation(threadId: string, turnId: string, runtime: RuntimeThread): void {
    const requestId = `plan:${turnId}`;
    if (this.pendingInteractions.has(requestId)) return;
    const sourceMessage = this.turnMessages.get(turnId) ?? {
      clientMessageId: `plan-source-${turnId}`,
      text: "",
      mode: "plan",
      model: runtime.model,
      imagePaths: [],
      mentionedFiles: [],
      reasoningEffort: runtime.reasoningEffort,
      approvalMode: "request",
      enqueuedAt: Date.now()
    };
    const interaction: PendingInteraction = {
      requestId,
      method: "item/tool/requestUserInput",
      threadId,
      turnId,
      itemId: `${turnId}-plan-confirmation`,
      params: {
        threadId,
        turnId,
        itemId: `${turnId}-plan-confirmation`,
        questions: [{
          id: "planDecision",
          header: "实施此计划？",
          question: "计划已经完成，是否开始实施？",
          isOther: true,
          isSecret: false,
          options: [
            { label: "是，实施此计划", description: "切换到执行模式并开始实施" },
            { label: "否，继续调整计划", description: "保留计划模式并继续修改" }
          ]
        }]
      },
      createdAt: Date.now()
    };
    this.planConfirmations.set(requestId, { threadId, turnId, sourceMessage });
    this.pendingInteractions.set(requestId, interaction);
    this.events.publish("interaction.requested", threadId, interaction);
  }

  private async flushQueue(threadId: string, runtime: RuntimeThread): Promise<void> {
    if (runtime.activeTurnId || runtime.starting || runtime.queuePaused || this.isDesktopThreadActive(threadId)) return;
    const next = runtime.queue.shift();
    this.publishQueue(threadId, runtime);
    if (!next) return;
    if (!next.clientMessageId.startsWith("retry-")) this.resetRetryChain(runtime);
    runtime.starting = this.startTurn(threadId, runtime, next)
      .catch((error) => {
        const detail = error instanceof Error ? error.message : String(error);
        const retryScheduled = this.scheduleRetry(
          threadId,
          `start-${Date.now()}`,
          "start_failed",
          detail,
          next,
          runtime,
          next.text
        );
        if (!retryScheduled) {
          runtime.queue.unshift(next);
          runtime.queuePaused = true;
          this.publishQueue(threadId, runtime);
        }
        this.events.publish("queue.failed", threadId, {
          clientMessageId: next.clientMessageId,
          error: detail,
          retryScheduled
        });
      })
      .finally(() => {
        runtime.starting = null;
        if (!runtime.activeTurnId && !runtime.queuePaused) void this.flushQueue(threadId, runtime);
      });
    await runtime.starting;
  }

  private scheduleRetry(
    threadId: string,
    turnId: string,
    turnStatus: string,
    reason: string,
    source: QueuedMessage,
    runtime: RuntimeThread,
    retryText?: string
  ): boolean {
    const policy = this.getRetryPolicy(threadId);
    const previousCount = runtime.retryStatus?.retryCount ?? 0;
    const canRetry = shouldRetry(policy, previousCount, runtime.retrySuppressed);
    runtime.retryStatus = {
      state: canRetry
        ? "scheduled"
        : runtime.retrySuppressed
          ? "cancelled"
          : policy.enabled
            ? "exhausted"
            : "failed",
      turnId,
      turnStatus,
      reason,
      retryCount: previousCount,
      scheduledAt: canRetry ? Date.now() + policy.delaySeconds * 1000 : null
    };
    if (!canRetry) {
      this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
      return false;
    }
    runtime.queuePaused = false;
    if (runtime.retryTimer) clearTimeout(runtime.retryTimer);
    runtime.retryTimer = setTimeout(() => {
      runtime.retryTimer = null;
      if (runtime.retrySuppressed || !this.getRetryPolicy(threadId).enabled) return;
      const attempt = (runtime.retryStatus?.retryCount ?? previousCount) + 1;
      const retryMessage: QueuedMessage = {
        ...source,
        clientMessageId: `retry-${Date.now()}-${attempt}`,
        text: retryText ?? this.getRetryPolicy(threadId).retryPrompt,
        imagePaths: [],
        mentionedFiles: [],
        enqueuedAt: Date.now()
      };
      runtime.retryStatus = {
        ...(runtime.retryStatus ?? { turnId, turnStatus, reason, retryCount: previousCount, scheduledAt: null }),
        state: "retrying",
        retryCount: attempt,
        scheduledAt: null
      };
      runtime.queue.unshift(retryMessage);
      this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
      this.publishQueue(threadId, runtime);
      void this.flushQueue(threadId, runtime);
    }, policy.delaySeconds * 1000);
    runtime.retryTimer.unref();
    this.events.publish("thread.retry.updated", threadId, this.getActivity(threadId));
    return true;
  }

  private failureReason(value: Record<string, any>, status: string, manuallyInterrupted: boolean): string {
    if (manuallyInterrupted) return "用户手动停止了本次任务";
    const candidates = [value.turn?.error, value.error, value.turn?.reason, value.reason, value.message];
    for (const candidate of candidates) {
      if (typeof candidate === "string" && candidate.trim()) return candidate.trim().slice(0, 2_000);
      if (candidate && typeof candidate === "object") {
        const message = candidate.message ?? candidate.detail ?? candidate.code;
        if (typeof message === "string" && message.trim()) return message.trim().slice(0, 2_000);
      }
    }
    return `任务以 ${status} 状态中断，Codex 未提供更详细的原因`;
  }

  private resetRetryChain(runtime: RuntimeThread): void {
    if (runtime.retryTimer) clearTimeout(runtime.retryTimer);
    runtime.retryTimer = null;
    runtime.retrySuppressed = false;
    runtime.retryStatus = null;
  }

  private normalizeTurn(threadId: string, turn: RawTurn, includeOperations: boolean): ChatRecord[] {
    const turnState: ChatRecord["state"] = turn.completedAt === null
      ? "streaming"
      : turn.status === "completed"
        ? "completed"
        : "interrupted";
    return turn.items
      .map((item) => this.normalizeItem(
        threadId,
        turn.id,
        item,
        item.type === "userMessage" ? turn.startedAt : turn.completedAt ?? turn.startedAt,
        turnState !== "streaming"
      ) ?? (includeOperations
        ? this.normalizeOperation(threadId, turn.id, item, turn.startedAt, turnState !== "streaming")
        : null))
      .map((record) => record ? { ...record, state: turnState } : null)
      .filter((item): item is ChatRecord => item !== null);
  }

  private normalizeOperation(
    threadId: string,
    turnId: string,
    item: RawItem,
    timestampSeconds: number | null,
    completed: boolean
  ): ChatRecord | null {
    const reasoning = item.type === "reasoning" || item.type === "plan";
    const text = operationItemText(item as Record<string, unknown>);
    const imagePaths = this.imagePaths(item);
    if (!text && imagePaths.length === 0) return null;
    return {
      threadId,
      turnId,
      itemId: item.id,
      role: "assistant",
      phase: "commentary",
      text,
      state: completed ? "completed" : "streaming",
      timestamp: timestampSeconds === null ? null : timestampSeconds * 1000,
      kind: reasoning ? "thinking" : "tool",
      tool: reasoning ? null : item.type,
      imagePaths
    };
  }

  private normalizeItem(
    threadId: string,
    turnId: string,
    item: RawItem,
    timestampSeconds: number | null,
    completed: boolean
  ): ChatRecord | null {
    if (item.type === "userMessage") {
      const text = (item.content ?? [])
        .filter((content) => content.type === "text" && typeof content.text === "string")
        .map((content) => content.text)
        .join("\n")
        .trim();
      const cleanedText = this.cleanUserText(text);
      const imagePaths = (item.content ?? [])
        .filter((content) => content.type === "localImage" && typeof (content as Record<string, unknown>).path === "string")
        .map((content) => String((content as Record<string, unknown>).path));
      if (!cleanedText && imagePaths.length === 0) return null;
      return {
        threadId,
        turnId,
        itemId: item.id,
        role: "user",
        phase: null,
        text: cleanedText,
        state: completed ? "completed" : "streaming",
        timestamp: timestampSeconds === null ? null : timestampSeconds * 1000,
        kind: "message",
        tool: null,
        imagePaths
      };
    }
    if (item.type !== "agentMessage" || typeof item.text !== "string") return null;
    const phase = item.phase === "commentary" || item.phase === "final" ? item.phase : null;
    return {
      threadId,
      turnId,
      itemId: item.id,
      role: "assistant",
      phase,
      text: item.text,
      state: completed ? "completed" : "streaming",
      timestamp: timestampSeconds === null ? null : timestampSeconds * 1000,
      kind: "message",
      tool: null
    };
  }

  private imagePaths(item: RawItem): string[] {
    if (item.type === "imageView" && typeof item.path === "string") return [item.path];
    if (item.type === "imageGeneration" && typeof item.savedPath === "string") return [item.savedPath];
    return [];
  }

  private toSummary(thread: RawThread, archived: boolean): ThreadSummary {
    return {
      id: thread.id,
      title: (thread.name?.trim() || thread.preview.trim() || "未命名会话").slice(0, 160),
      projectName: basename(thread.cwd) || thread.cwd,
      cwd: thread.cwd,
      source: this.sourceName(thread.source),
      updatedAt: thread.updatedAt * 1000,
      archived,
      status: thread.status.type
    };
  }

  private cleanUserText(text: string): string {
    if (!text.startsWith("# Files mentioned by the user:")) return text;
    const marker = "## My request for Codex:";
    const requestIndex = text.indexOf(marker);
    return requestIndex >= 0 ? text.slice(requestIndex + marker.length).trim() : text;
  }

  private sourceName(source: unknown): string {
    if (typeof source === "string") return source;
    if (source && typeof source === "object") {
      if ("custom" in source) return String((source as { custom: unknown }).custom);
      if ("subAgent" in source) return "subAgent";
    }
    return "unknown";
  }

  private runtime(threadId: string): RuntimeThread {
    let runtime = this.runtimes.get(threadId);
    if (!runtime) {
      runtime = {
        loaded: false,
        activeTurnId: null,
        model: null,
        reasoningEffort: null,
        queue: [],
        queuePaused: false,
        starting: null,
        cwd: null,
        retryStatus: null,
        retryTimer: null,
        retrySuppressed: false
      };
      this.runtimes.set(threadId, runtime);
    }
    return runtime;
  }

  private pruneClientMessageIds(): void {
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    for (const [id, timestamp] of this.clientMessages) if (timestamp < cutoff) this.clientMessages.delete(id);
    if (this.clientMessages.size <= 10_000) return;
    const excess = this.clientMessages.size - 10_000;
    for (const id of [...this.clientMessages.keys()].slice(0, excess)) this.clientMessages.delete(id);
  }

  private loadThreadQueues(): void {
    try {
      const parsed = JSON.parse(readFileSync(this.config.threadQueueFile, "utf8")) as {
        threads?: Record<string, { paused?: boolean; messages?: QueuedMessage[] }>;
      };
      for (const [threadId, value] of Object.entries(parsed.threads ?? {})) {
        if (!Array.isArray(value.messages)) continue;
        const messages = value.messages.filter((message) =>
          message && typeof message.clientMessageId === "string" && typeof message.text === "string"
        ).slice(0, this.config.queueLimitPerThread).map((message): QueuedMessage => ({
          clientMessageId: message.clientMessageId,
          text: message.text,
          mode: (message.mode === "plan" || message.mode === "agent" ? message.mode : "default") as CollaborationModeName,
          model: typeof message.model === "string" ? message.model : null,
          imagePaths: Array.isArray(message.imagePaths) ? message.imagePaths.filter((value): value is string => typeof value === "string") : [],
          mentionedFiles: Array.isArray(message.mentionedFiles) ? message.mentionedFiles.filter((value): value is { name: string; path: string } =>
            Boolean(value) && typeof value.name === "string" && typeof value.path === "string"
          ) : [],
          reasoningEffort: typeof message.reasoningEffort === "string" ? message.reasoningEffort : null,
          approvalMode: (message.approvalMode === "auto" || message.approvalMode === "fullAccess"
            ? message.approvalMode
            : "request") as ApprovalMode,
          enqueuedAt: Number.isFinite(message.enqueuedAt) ? message.enqueuedAt : Date.now()
        }));
        if (messages.length === 0) continue;
        const runtime = this.runtime(threadId);
        runtime.queue = messages;
        runtime.queuePaused = Boolean(value.paused);
        for (const message of messages) this.clientMessages.set(message.clientMessageId, message.enqueuedAt || Date.now());
      }
    } catch {
      // First launch or a damaged optional queue file starts with no queued messages.
    }
  }

  private persistThreadQueues(): void {
    const threads: Record<string, { paused: boolean; messages: QueuedMessage[] }> = {};
    for (const [threadId, runtime] of this.runtimes) {
      if (runtime.queue.length > 0) {
        threads[threadId] = { paused: runtime.queuePaused, messages: runtime.queue };
      }
    }
    mkdirSync(dirname(this.config.threadQueueFile), { recursive: true });
    const temporary = `${this.config.threadQueueFile}.tmp`;
    writeFileSync(temporary, JSON.stringify({ version: 1, threads }, null, 2), "utf8");
    renameSync(temporary, this.config.threadQueueFile);
  }

  private loadThreadInstructions(): void {
    try {
      const parsed = JSON.parse(readFileSync(this.config.threadInstructionsFile, "utf8")) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return;
      for (const [threadId, value] of Object.entries(parsed as Record<string, unknown>)) {
        if (typeof value === "string" && value.trim()) {
          this.threadInstructions.set(threadId, value.trim().slice(0, MAX_THREAD_INSTRUCTIONS_CHARS));
        }
      }
    } catch {
      // First launch or a damaged optional preferences file starts with no per-thread instructions.
    }
  }

  private persistThreadInstructions(): void {
    mkdirSync(dirname(this.config.threadInstructionsFile), { recursive: true });
    const temporary = `${this.config.threadInstructionsFile}.tmp`;
    writeFileSync(temporary, JSON.stringify(Object.fromEntries(this.threadInstructions), null, 2), "utf8");
    renameSync(temporary, this.config.threadInstructionsFile);
  }

  private loadThreadRetryPolicies(): void {
    try {
      const parsed = JSON.parse(readFileSync(this.config.threadRetrySettingsFile, "utf8")) as Record<string, RetryPolicy>;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return;
      for (const [threadId, policy] of Object.entries(parsed)) {
        if (policy && typeof policy === "object") this.threadRetryPolicies.set(threadId, {
          enabled: Boolean(policy.enabled),
          maxRetries: Math.max(1, Math.min(20, Math.trunc(policy.maxRetries || 3))),
          untilSuccess: Boolean(policy.untilSuccess),
          delaySeconds: Math.max(1, Math.min(300, Math.trunc(policy.delaySeconds || 5))),
          retryPrompt: typeof policy.retryPrompt === "string" && policy.retryPrompt.trim()
            ? policy.retryPrompt.trim().slice(0, MAX_RETRY_PROMPT_CHARS)
            : DEFAULT_RETRY_PROMPT
        });
      }
    } catch {
      // First launch or a damaged optional preferences file uses safe defaults.
    }
  }

  private persistThreadRetryPolicies(): void {
    mkdirSync(dirname(this.config.threadRetrySettingsFile), { recursive: true });
    const temporary = `${this.config.threadRetrySettingsFile}.tmp`;
    writeFileSync(temporary, JSON.stringify(Object.fromEntries(this.threadRetryPolicies), null, 2), "utf8");
    renameSync(temporary, this.config.threadRetrySettingsFile);
  }
}

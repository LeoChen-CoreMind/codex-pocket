import { mkdirSync, readFileSync, realpathSync, renameSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, relative, resolve } from "node:path";
import { randomUUID } from "node:crypto";

import type { EventBus } from "./event-bus.js";

export type VsCodeCommandType = "focus" | "newChat" | "openThread" | "closeThread" | "openFile";

export interface VsCodeOpenThread {
  threadId: string;
  label: string;
  active: boolean;
  running: boolean;
  terminalStatus: "completed" | "aborted" | null;
  activityUpdatedAt: number | null;
}

export interface VsCodeInstanceRegistration {
  instanceId: string;
  editorName: string;
  windowTitle: string;
  workspaceName: string | null;
  workspaceFolders: string[];
  processId: number;
  extensionHostPid: number;
  machineName: string;
  vscodeVersion: string;
  openThreads: VsCodeOpenThread[];
}

export interface VsCodeInstance extends VsCodeInstanceRegistration {
  lastSeenAt: number;
  online: boolean;
  bound: boolean;
}

export interface VsCodeCommand {
  id: string;
  sequence: number;
  type: VsCodeCommandType;
  threadId?: string;
  path?: string;
  createdAt: number;
}

export interface OnlineConversation {
  instanceId: string;
  threadId: string;
  title: string;
  active: boolean;
  editorName: string;
  windowTitle: string;
  workspaceName: string | null;
  workspaceFolders: string[];
  machineName: string;
  lastSeenAt: number;
  bound: boolean;
}

interface RuntimeInstance extends VsCodeInstanceRegistration {
  lastSeenAt: number;
  commands: VsCodeCommand[];
  nextSequence: number;
  waiters: Set<() => void>;
  lastCompletedSequence: number;
}

interface PersistedBinding {
  boundInstanceId: string | null;
}

const ONLINE_WINDOW_MS = 12_000;
const COMMAND_HISTORY_LIMIT = 100;

export class VsCodeInstanceService {
  private readonly instances = new Map<string, RuntimeInstance>();
  private boundInstanceId: string | null;
  private readonly onlineSweep: NodeJS.Timeout;
  private lastOnlineIds = "";

  constructor(
    private readonly stateFile: string,
    private readonly events: EventBus
  ) {
    this.boundInstanceId = this.readBinding();
    this.onlineSweep = setInterval(() => this.publishOnlineTransition(), 2_000);
    this.onlineSweep.unref();
  }

  register(input: VsCodeInstanceRegistration): VsCodeInstance {
    const existing = this.instances.get(input.instanceId);
    const changed = !existing || this.registrationFingerprint(existing) !== this.registrationFingerprint(input);
    const instance: RuntimeInstance = {
      ...input,
      lastSeenAt: Date.now(),
      commands: existing?.commands ?? [],
      nextSequence: existing?.nextSequence ?? 1,
      waiters: existing?.waiters ?? new Set(),
      lastCompletedSequence: existing?.lastCompletedSequence ?? 0
    };
    this.instances.set(input.instanceId, instance);
    // Keep an explicit persisted binding across Bridge restarts. At startup the selected editor
    // may register a few seconds after another editor; replacing it here would silently route the
    // first command to whichever window happened to register first.
    if (this.boundInstanceId === null) {
      this.boundInstanceId = input.instanceId;
      this.writeBinding();
      this.events.publish("vscode.binding.changed", null, { boundInstanceId: this.boundInstanceId });
    }
    if (changed) this.events.publish("vscode.instances.changed", null, { instanceId: input.instanceId });
    this.publishOnlineTransition();
    return this.toPublic(instance);
  }

  list(): { data: VsCodeInstance[]; boundInstanceId: string | null } {
    const data = [...this.instances.values()]
      .map((instance) => this.toPublic(instance))
      .sort((a, b) => Number(b.online) - Number(a.online) || b.lastSeenAt - a.lastSeenAt);
    return { data, boundInstanceId: this.boundInstanceId };
  }

  bind(instanceId: string | null): { boundInstanceId: string | null } {
    if (instanceId !== null) {
      const instance = this.instances.get(instanceId);
      if (!instance || !this.isOnline(instance)) throw new Error("Editor instance is not online");
    }
    this.boundInstanceId = instanceId;
    this.writeBinding();
    this.events.publish("vscode.binding.changed", null, { boundInstanceId: this.boundInstanceId });
    return { boundInstanceId: this.boundInstanceId };
  }

  onlineConversations(): { data: OnlineConversation[]; boundInstanceId: string | null } {
    const data = [...this.instances.values()]
      .filter((instance) => this.isOnline(instance))
      .flatMap((instance) => instance.openThreads.map((thread) => ({
        instanceId: instance.instanceId,
        threadId: thread.threadId,
        title: thread.label || "Codex",
        active: thread.active,
        editorName: instance.editorName,
        windowTitle: instance.windowTitle,
        workspaceName: instance.workspaceName,
        workspaceFolders: [...instance.workspaceFolders],
        machineName: instance.machineName,
        lastSeenAt: instance.lastSeenAt,
        bound: instance.instanceId === this.boundInstanceId
      })))
      .sort((left, right) => Number(right.active) - Number(left.active) || right.lastSeenAt - left.lastSeenAt);
    return { data, boundInstanceId: this.boundInstanceId };
  }

  getBoundInstance(requireOnline = true): VsCodeInstance | null {
    if (!this.boundInstanceId) return null;
    const instance = this.instances.get(this.boundInstanceId);
    if (!instance) return null;
    if (requireOnline && !this.isOnline(instance)) return null;
    return this.toPublic(instance);
  }

  requireBoundInstance(): VsCodeInstance {
    const instance = this.getBoundInstance(true);
    if (!instance) throw new Error("Bind an online editor window first");
    return instance;
  }

  workspaceForNewThread(): string {
    const instance = this.requireBoundInstance();
    const workspace = instance.workspaceFolders[0];
    if (!workspace) throw new Error("The selected editor window has no open workspace");
    return workspace;
  }

  assertWorkspaceAllowed(cwd: string): void {
    const instance = this.requireBoundInstance();
    if (!instance.workspaceFolders.some((root) => this.pathContains(root, cwd))) {
      throw new Error("The thread is outside the bound editor workspace");
    }
  }

  isWorkspaceAllowed(cwd: string): boolean {
    const instance = this.getBoundInstance(true);
    return instance?.workspaceFolders.some((root) => this.pathContains(root, cwd)) ?? false;
  }

  enqueue(type: VsCodeCommandType, threadId?: string, path?: string): VsCodeCommand {
    const bound = this.requireBoundInstance();
    return this.enqueueFor(bound.instanceId, type, threadId, path);
  }

  enqueueFor(instanceId: string, type: VsCodeCommandType, threadId?: string, path?: string): VsCodeCommand {
    const instance = this.instances.get(instanceId);
    if (!instance || !this.isOnline(instance)) throw new Error("Editor instance is not online");
    const command: VsCodeCommand = {
      id: randomUUID(),
      sequence: instance.nextSequence++,
      type,
      ...(threadId ? { threadId } : {}),
      ...(path ? { path } : {}),
      createdAt: Date.now()
    };
    instance.commands.push(command);
    if (instance.commands.length > COMMAND_HISTORY_LIMIT) {
      instance.commands.splice(0, instance.commands.length - COMMAND_HISTORY_LIMIT);
    }
    for (const wake of instance.waiters) wake();
    instance.waiters.clear();
    return command;
  }

  close(): void {
    clearInterval(this.onlineSweep);
  }

  async poll(instanceId: string, after: number, waitMs: number): Promise<VsCodeCommand[]> {
    const instance = this.instances.get(instanceId);
    if (!instance) throw new Error("Editor instance is not registered");
    instance.lastSeenAt = Date.now();
    const pending = () => instance.commands.filter(
      (command) => command.sequence > Math.max(after, instance.lastCompletedSequence)
    );
    const ready = pending();
    if (ready.length > 0 || waitMs <= 0) return ready;

    await new Promise<void>((resolvePromise) => {
      let settled = false;
      const finish = () => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        instance.waiters.delete(finish);
        resolvePromise();
      };
      const timer = setTimeout(finish, Math.min(waitMs, 25_000));
      instance.waiters.add(finish);
    });
    instance.lastSeenAt = Date.now();
    return pending();
  }

  heartbeat(instanceId: string): void {
    const instance = this.instances.get(instanceId);
    if (!instance) throw new Error("Editor instance is not registered");
    instance.lastSeenAt = Date.now();
  }

  unregister(instanceId: string): void {
    const instance = this.instances.get(instanceId);
    if (!instance) return;
    for (const wake of instance.waiters) wake();
    instance.waiters.clear();
    this.instances.delete(instanceId);
    if (this.boundInstanceId === instanceId) {
      this.boundInstanceId = null;
      this.writeBinding();
      this.events.publish("vscode.binding.changed", null, { boundInstanceId: null });
    }
    this.events.publish("vscode.instances.changed", null, { instanceId, online: false });
    this.publishOnlineTransition();
  }

  complete(instanceId: string, sequence: number): void {
    const instance = this.instances.get(instanceId);
    if (!instance) throw new Error("Editor instance is not registered");
    instance.lastSeenAt = Date.now();
    instance.lastCompletedSequence = Math.max(instance.lastCompletedSequence, sequence);
    instance.commands = instance.commands.filter((command) => command.sequence > instance.lastCompletedSequence);
  }

  private toPublic(instance: RuntimeInstance): VsCodeInstance {
    return {
      instanceId: instance.instanceId,
      editorName: instance.editorName,
      windowTitle: instance.windowTitle,
      workspaceName: instance.workspaceName,
      workspaceFolders: [...instance.workspaceFolders],
      processId: instance.processId,
      extensionHostPid: instance.extensionHostPid,
      machineName: instance.machineName,
      vscodeVersion: instance.vscodeVersion,
      openThreads: instance.openThreads.map((thread) => ({ ...thread })),
      lastSeenAt: instance.lastSeenAt,
      online: this.isOnline(instance),
      bound: instance.instanceId === this.boundInstanceId
    };
  }

  private isOnline(instance: RuntimeInstance): boolean {
    return Date.now() - instance.lastSeenAt <= ONLINE_WINDOW_MS;
  }

  private registrationFingerprint(instance: VsCodeInstanceRegistration): string {
    return JSON.stringify({
      editorName: instance.editorName,
      windowTitle: instance.windowTitle,
      workspaceName: instance.workspaceName,
      workspaceFolders: instance.workspaceFolders,
      processId: instance.processId,
      extensionHostPid: instance.extensionHostPid,
      machineName: instance.machineName,
      vscodeVersion: instance.vscodeVersion,
      openThreads: instance.openThreads
    });
  }

  private publishOnlineTransition(): void {
    const onlineIds = [...this.instances.values()]
      .filter((instance) => this.isOnline(instance))
      .map((instance) => instance.instanceId)
      .sort()
      .join(",");
    if (onlineIds === this.lastOnlineIds) return;
    this.lastOnlineIds = onlineIds;
    this.events.publish("vscode.instances.changed", null, { onlineInstanceIds: onlineIds ? onlineIds.split(",") : [] });
  }

  private pathContains(root: string, candidate: string): boolean {
    let rootPath: string;
    let candidatePath: string;
    try {
      rootPath = realpathSync.native(root).toLocaleLowerCase("en-US");
      candidatePath = realpathSync.native(candidate).toLocaleLowerCase("en-US");
    } catch {
      return false;
    }
    const child = relative(rootPath, candidatePath);
    return child === "" || (!child.startsWith("..") && !isAbsolute(child));
  }

  private readBinding(): string | null {
    try {
      const value = JSON.parse(readFileSync(this.stateFile, "utf8")) as PersistedBinding;
      return typeof value.boundInstanceId === "string" ? value.boundInstanceId : null;
    } catch {
      return null;
    }
  }

  private writeBinding(): void {
    mkdirSync(dirname(this.stateFile), { recursive: true });
    const temporary = `${this.stateFile}.${process.pid}.tmp`;
    writeFileSync(temporary, JSON.stringify({ boundInstanceId: this.boundInstanceId }), "utf8");
    renameSync(temporary, this.stateFile);
  }
}

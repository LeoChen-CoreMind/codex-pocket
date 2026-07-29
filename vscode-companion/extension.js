const crypto = require("node:crypto");
const fs = require("node:fs");
const http = require("node:http");
const https = require("node:https");
const os = require("node:os");
const path = require("node:path");
const vscode = require("vscode");

const REQUEST_TIMEOUT_MS = 35_000;
const REGISTER_DEBOUNCE_MS = 150;
const ROLLOUT_SCAN_INTERVAL_MS = 2_000;

class Companion {
  constructor(context) {
    this.context = context;
    // One extension host represents one editor window. A persisted workspace-scoped id can be
    // shared by two windows of the same workspace and would collapse them into one online row.
    this.instanceId = crypto.randomUUID();
    this.stopped = false;
    this.lastSequence = 0;
    this.registerTimer = null;
    this.rolloutFiles = new Map();
    this.rolloutStates = new Map();
    this.lastRolloutScanAt = 0;
    this.pocketActivities = new Map();
    this.lastConnectionError = null;
    this.registered = false;
    this.heartbeatTimer = null;
    this.status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 20);
    this.status.name = "Codex Pocket";
    this.status.command = "codexPocket.showInstance";
    this.status.text = "$(debug-disconnect) Codex Pocket";
    this.status.tooltip = "Codex Pocket Bridge is connecting";
    this.status.show();
    context.subscriptions.push(this.status);
  }

  start() {
    this.heartbeatTimer = setInterval(() => {
      if (!this.registered || this.stopped) return;
      void this.request("POST", `/internal/vscode-companion/${this.instanceId}/heartbeat`, {})
        .then(() => this.setConnected(true))
        .catch((error) => this.setConnected(false, error));
    }, 4_000);
    void this.run();
  }

  stop() {
    this.stopped = true;
    if (this.registerTimer) clearTimeout(this.registerTimer);
    this.registerTimer = null;
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = null;
    this.registered = false;
    void this.request("POST", `/internal/vscode-companion/${this.instanceId}/offline`, {})
      .catch(() => {});
  }

  async run() {
    let delay = 1_000;
    while (!this.stopped) {
      try {
        await this.register();
        this.registered = true;
        this.setConnected(true);
        delay = 1_000;
        await this.pollUntilFailure();
      } catch (error) {
        if (!this.stopped) this.setConnected(false, error);
      }
      if (!this.stopped) {
        await new Promise((resolve) => setTimeout(resolve, delay));
        delay = Math.min(delay * 2, 10_000);
      }
    }
  }

  async register() {
    this.refreshRolloutActivities();
    const workspaceFolders = (vscode.workspace.workspaceFolders || [])
      .filter((folder) => folder.uri.scheme === "file")
      .map((folder) => folder.uri.fsPath);
    const workspaceName = vscode.workspace.name || null;
    const configuredCli = vscode.workspace.getConfiguration("chatgpt").get("cliExecutable");
    await this.request("POST", "/internal/vscode-companion/register", {
      instanceId: this.instanceId,
      editorName: vscode.env.appName,
      windowTitle: workspaceName ? `${workspaceName} - ${vscode.env.appName}` : vscode.env.appName,
      workspaceName,
      workspaceFolders,
      processId: process.ppid,
      extensionHostPid: process.pid,
      machineName: os.hostname(),
      vscodeVersion: vscode.version,
      codexCliExecutable: typeof configuredCli === "string" && configuredCli ? configuredCli : null,
      openThreads: this.openThreads()
    });
  }

  scheduleRegister() {
    if (this.stopped) return;
    if (this.registerTimer) clearTimeout(this.registerTimer);
    this.registerTimer = setTimeout(() => {
      this.registerTimer = null;
      void this.register().catch((error) => this.setConnected(false, error));
    }, REGISTER_DEBOUNCE_MS);
  }

  openThreads() {
    const threads = new Map();
    for (const group of vscode.window.tabGroups.all) {
      for (const tab of group.tabs) {
        const threadId = threadIdFromTab(tab);
        if (!threadId) continue;
        const existing = threads.get(threadId);
        const activity = this.rolloutStates.get(threadId);
        threads.set(threadId, {
          threadId,
          label: tab.label,
          active: Boolean(tab.isActive || existing?.active),
          running: Boolean(activity?.running),
          terminalStatus: activity?.terminalStatus || null,
          activityUpdatedAt: activity?.updatedAt || null
        });
      }
    }
    return [...threads.values()];
  }

  async pollUntilFailure() {
    while (!this.stopped) {
      if (this.refreshRolloutActivities()) await this.register();
      const result = await this.request(
        "GET",
        `/internal/vscode-companion/${this.instanceId}/commands?after=${this.lastSequence}&wait=1000`
      );
      this.setConnected(true);
      this.pocketActivities = new Map(
        (Array.isArray(result.activity) ? result.activity : []).map((activity) => [activity.threadId, activity])
      );
      this.updateStatus();
      const commands = Array.isArray(result.data) ? result.data : [];
      for (const command of commands) {
        await this.execute(command);
        this.lastSequence = Math.max(this.lastSequence, Number(command.sequence) || 0);
        await this.request("POST", `/internal/vscode-companion/${this.instanceId}/complete`, {
          sequence: this.lastSequence
        });
      }
    }
  }

  refreshRolloutActivities() {
    const now = Date.now();
    if (now - this.lastRolloutScanAt >= ROLLOUT_SCAN_INTERVAL_MS) {
      this.lastRolloutScanAt = now;
      this.rolloutFiles = scanRolloutFiles();
    }
    let changed = false;
    for (const thread of this.openThreadsWithoutActivity()) {
      const file = this.rolloutFiles.get(thread.threadId);
      if (!file) continue;
      let state = this.rolloutStates.get(thread.threadId);
      if (!state || state.file !== file) {
        state = { file, offset: 0, remainder: "", running: false, terminalStatus: null, updatedAt: 0 };
        this.rolloutStates.set(thread.threadId, state);
      }
      changed = consumeRolloutEvents(state) || changed;
    }
    return changed;
  }

  openThreadsWithoutActivity() {
    const threads = new Map();
    for (const group of vscode.window.tabGroups.all) {
      for (const tab of group.tabs) {
        const threadId = threadIdFromTab(tab);
        if (threadId) threads.set(threadId, { threadId, active: Boolean(tab.isActive) });
      }
    }
    return [...threads.values()];
  }

  activeThreadId() {
    for (const group of vscode.window.tabGroups.all) {
      const active = group.tabs.find((tab) => tab.isActive);
      const threadId = active ? threadIdFromTab(active) : null;
      if (threadId) return threadId;
    }
    return null;
  }

  updateStatus() {
    const threadId = this.activeThreadId();
    const pocket = threadId ? this.pocketActivities.get(threadId) : null;
    const desktop = threadId ? this.rolloutStates.get(threadId) : null;
    if (pocket?.active && pocket.source === "pocket" && pocket.turnId) {
      this.status.text = "$(debug-stop) Codex Pocket: 停止";
      this.status.tooltip = "停止当前 Pocket 任务";
      this.status.command = "codexPocket.stopActiveTurn";
    } else if (desktop?.running) {
      this.status.text = "$(sync~spin) Codex 桌面运行中";
      this.status.tooltip = "此任务由编辑器 Codex 独立运行";
      this.status.command = "codexPocket.showInstance";
    } else {
      this.status.text = "$(vm-active) Codex Pocket";
      this.status.tooltip = `Connected as ${this.instanceId}`;
      this.status.command = "codexPocket.showInstance";
    }
  }

  async stopActiveTurn() {
    const threadId = this.activeThreadId();
    const activity = threadId ? this.pocketActivities.get(threadId) : null;
    if (!threadId || !activity?.turnId || activity.source !== "pocket") return;
    await this.request(
      "POST",
      `/internal/vscode-companion/${this.instanceId}/threads/${encodeURIComponent(threadId)}/interrupt`,
      { expectedTurnId: activity.turnId }
    );
  }

  async execute(command) {
    try {
      switch (command.type) {
        case "focus":
          await vscode.commands.executeCommand("chatgpt.openSidebar");
          break;
        case "newChat":
          await vscode.commands.executeCommand("chatgpt.openSidebar");
          await vscode.commands.executeCommand("chatgpt.newChat");
          break;
        case "openThread": {
          if (typeof command.threadId !== "string" || !command.threadId) {
            throw new Error("openThread command is missing threadId");
          }
          const uri = vscode.Uri.from({
            scheme: "openai-codex",
            authority: "route",
            path: `/local/${command.threadId}`
          });
          await vscode.commands.executeCommand(
            "vscode.openWith",
            uri,
            "chatgpt.conversationEditor",
            { preview: false, preserveFocus: false }
          );
          await this.register();
          break;
        }
        case "configureCodexProxy": {
          if (typeof command.path !== "string" || !path.isAbsolute(command.path)) {
            throw new Error("configureCodexProxy command is missing an absolute path");
          }
          const configuration = vscode.workspace.getConfiguration("chatgpt");
          const current = configuration.get("cliExecutable");
          const normalize = (value) => path.resolve(value).toLocaleLowerCase("en-US");
          if (typeof current === "string" && current && normalize(current) === normalize(command.path)) break;
          await configuration.update("cliExecutable", command.path, vscode.ConfigurationTarget.Global);
          const reloadLabel = "\u91cd\u65b0\u52a0\u8f7d";
          void Promise.resolve(vscode.window.showInformationMessage(
            "Codex Pocket \u5df2\u914d\u7f6e\u5171\u4eab\u8fde\u63a5\uff0c\u91cd\u65b0\u52a0\u8f7d\u540e\u751f\u6548\u3002",
            reloadLabel
          )).then((selected) => selected === reloadLabel
            ? vscode.commands.executeCommand("workbench.action.reloadWindow")
            : undefined
          ).catch((error) => console.error(
            `[Codex Pocket] Reload prompt failed: ${error instanceof Error ? error.message : String(error)}`
          ));
          break;
        }
        case "closeThread": {
          if (typeof command.threadId !== "string" || !command.threadId) {
            throw new Error("closeThread command is missing threadId");
          }
          const tabs = vscode.window.tabGroups.all
            .flatMap((group) => group.tabs)
            .filter((tab) => threadIdFromTab(tab) === command.threadId);
          if (tabs.length > 0) {
            await vscode.window.tabGroups.close(tabs, true);
          }
          await this.register();
          break;
        }
        case "openFile": {
          if (typeof command.path !== "string" || !path.isAbsolute(command.path)) {
            throw new Error("openFile command is missing an absolute path");
          }
          const document = await vscode.workspace.openTextDocument(vscode.Uri.file(command.path));
          await vscode.window.showTextDocument(document, { preview: false, preserveFocus: false });
          break;
        }
        default:
          throw new Error(`Unsupported Codex Pocket command: ${String(command.type)}`);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      void vscode.window.showWarningMessage(`Codex Pocket command failed: ${message}`);
    }
  }

  request(method, requestPath, body) {
    const base = new URL(
      vscode.workspace.getConfiguration("codexPocket").get("bridgeUrl", "http://127.0.0.1:47831")
    );
    if (!isLoopback(base.hostname)) throw new Error("Companion Bridge URL must use a loopback address");
    const token = readBridgeToken();
    const payload = body === undefined ? null : Buffer.from(JSON.stringify(body), "utf8");
    const transport = base.protocol === "https:" ? https : http;
    const basePath = base.pathname.endsWith("/") ? base.pathname.slice(0, -1) : base.pathname;
    return new Promise((resolve, reject) => {
      const request = transport.request({
        protocol: base.protocol,
        hostname: base.hostname,
        port: base.port,
        method,
        path: `${basePath}${requestPath}`,
        timeout: REQUEST_TIMEOUT_MS,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
          ...(payload ? {
            "Content-Type": "application/json",
            "Content-Length": String(payload.length)
          } : {})
        }
      }, (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8");
          let result = {};
          try {
            result = text ? JSON.parse(text) : {};
          } catch {
            reject(new Error(`Bridge returned invalid JSON (${response.statusCode})`));
            return;
          }
          if ((response.statusCode || 500) >= 400) {
            reject(new Error(result.error || result.message || `Bridge request failed (${response.statusCode})`));
            return;
          }
          resolve(result);
        });
      });
      request.on("timeout", () => request.destroy(new Error("Bridge request timed out")));
      request.on("error", reject);
      if (payload) request.write(payload);
      request.end();
    });
  }

  setConnected(connected, error) {
    if (connected) {
      this.lastConnectionError = null;
      this.updateStatus();
    }
    else {
      const message = error instanceof Error ? error.message : String(error || "unknown error");
      if (message !== this.lastConnectionError) {
        this.lastConnectionError = message;
        console.error(`[Codex Pocket] Companion connection failed: ${message}`);
      }
      this.status.text = "$(debug-disconnect) Codex Pocket";
      this.status.tooltip = `Disconnected: ${message}`;
      this.status.command = "codexPocket.showInstance";
    }
  }
}

function scanRolloutFiles() {
  const result = new Map();
  const root = path.join(process.env.CODEX_HOME || path.join(os.homedir(), ".codex"), "sessions");
  const visit = (directory) => {
    let entries;
    try { entries = fs.readdirSync(directory, { withFileTypes: true }); } catch { return; }
    for (const entry of entries) {
      const fullPath = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(fullPath);
      else {
        const match = /^rollout-.*-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.jsonl$/i.exec(entry.name);
        if (match) result.set(match[1], fullPath);
      }
    }
  };
  visit(root);
  return result;
}

function consumeRolloutEvents(state) {
  let stats;
  try { stats = fs.statSync(state.file); } catch { return false; }
  if (stats.size < state.offset) {
    state.offset = 0;
    state.remainder = "";
  }
  if (stats.size === state.offset) return false;
  const fd = fs.openSync(state.file, "r");
  let changed = false;
  try {
    const buffer = Buffer.allocUnsafe(64 * 1024);
    while (state.offset < stats.size) {
      const count = fs.readSync(fd, buffer, 0, Math.min(buffer.length, stats.size - state.offset), state.offset);
      if (count <= 0) break;
      state.offset += count;
      const lines = (state.remainder + buffer.toString("utf8", 0, count)).split(/\r?\n/);
      state.remainder = lines.pop() || "";
      for (const line of lines) {
        let event;
        try { event = JSON.parse(line); } catch { continue; }
        const type = event?.type === "event_msg" ? event?.payload?.type : null;
        if (type === "task_started") {
          changed = changed || !state.running;
          state.running = true;
          state.terminalStatus = null;
        } else if (type === "task_complete" || type === "turn_aborted") {
          const terminalStatus = type === "task_complete" ? "completed" : "aborted";
          changed = changed || state.running || state.terminalStatus !== terminalStatus;
          state.running = false;
          state.terminalStatus = terminalStatus;
        } else {
          continue;
        }
        state.updatedAt = Number.isFinite(Date.parse(event.timestamp)) ? Date.parse(event.timestamp) : stats.mtimeMs;
      }
    }
  } finally {
    fs.closeSync(fd);
  }
  return changed;
}

function threadIdFromTab(tab) {
  const input = tab?.input;
  const uri = input?.uri;
  if (!uri || uri.scheme !== "openai-codex" || uri.authority !== "route") return null;
  const match = /^\/local\/([^/?#]+)$/.exec(uri.path);
  return match ? decodeURIComponent(match[1]) : null;
}

function isLoopback(hostname) {
  const value = hostname.replace(/^\[|\]$/g, "").toLowerCase();
  return value === "127.0.0.1" || value === "localhost" || value === "::1";
}

function readBridgeToken() {
  const localAppData = process.env.LOCALAPPDATA;
  if (!localAppData) throw new Error("LOCALAPPDATA is not set");
  const token = fs.readFileSync(path.join(localAppData, "CodexMobileBridge", "bridge.token"), "utf8").trim();
  if (!token) throw new Error("Codex Pocket Bridge token is empty");
  return token;
}

function activate(context) {
  const companion = new Companion(context);
  context.subscriptions.push(
    vscode.commands.registerCommand("codexPocket.showInstance", () => {
      void vscode.window.showInformationMessage(`Codex Pocket instance: ${companion.instanceId}`);
    }),
    vscode.commands.registerCommand("codexPocket.stopActiveTurn", () => {
      void companion.stopActiveTurn().catch((error) => {
        void vscode.window.showWarningMessage(`Codex Pocket stop failed: ${error.message || String(error)}`);
      });
    }),
    vscode.window.tabGroups.onDidChangeTabs(() => companion.scheduleRegister()),
    vscode.window.tabGroups.onDidChangeTabGroups(() => companion.scheduleRegister()),
    { dispose: () => companion.stop() }
  );
  companion.start();
}

function deactivate() {}

module.exports = { activate, deactivate };

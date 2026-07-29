const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const Module = require("node:module");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

test("a reload prompt does not block command polling or heartbeats", async () => {
  const calls = { register: 0, commands: 0, complete: 0, heartbeat: 0, configured: 0 };
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), "codex-pocket-companion-"));
  const previousLocalAppData = process.env.LOCALAPPDATA;
  process.env.LOCALAPPDATA = temporary;
  fs.mkdirSync(path.join(temporary, "CodexMobileBridge"), { recursive: true });
  fs.writeFileSync(path.join(temporary, "CodexMobileBridge", "bridge.token"), "test-token");

  const server = http.createServer((request, response) => {
    const send = (body) => {
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify(body));
    };
    if (request.url === "/internal/vscode-companion/register") {
      calls.register++;
      return send({});
    }
    if (request.url.includes("/commands")) {
      calls.commands++;
      return setTimeout(() => send({
        data: calls.complete ? [] : [{ sequence: 1, type: "configureCodexProxy", path: "C:\\proxy.exe" }],
        activity: [],
      }), 25);
    }
    if (request.url.endsWith("/complete")) {
      calls.complete++;
      return send({});
    }
    if (request.url.endsWith("/heartbeat")) {
      calls.heartbeat++;
      return send({});
    }
    if (request.url.endsWith("/offline")) return send({});
    response.statusCode = 404;
    return send({});
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));

  const disposable = () => ({ dispose() {} });
  const status = { show() {}, dispose() {} };
  const bridgeUrl = `http://127.0.0.1:${server.address().port}`;
  const vscode = {
    StatusBarAlignment: { Left: 1 },
    ConfigurationTarget: { Global: 1 },
    env: { appName: "Test Editor" },
    version: "1.107.0",
    window: {
      createStatusBarItem: () => status,
      showInformationMessage: () => new Promise(() => {}),
      showWarningMessage: () => Promise.resolve(),
      tabGroups: {
        all: [],
        onDidChangeTabs: disposable,
        onDidChangeTabGroups: disposable,
        close: async () => {},
      },
    },
    workspace: {
      workspaceFolders: [],
      name: "test",
      getConfiguration: (section) => section === "codexPocket"
        ? { get: () => bridgeUrl }
        : { get: () => null, update: async () => { calls.configured++; } },
    },
    commands: { registerCommand: disposable, executeCommand: async () => {} },
  };

  const originalLoad = Module._load;
  Module._load = function load(request, parent, isMain) {
    if (request === "vscode") return vscode;
    return originalLoad.call(this, request, parent, isMain);
  };
  const extensionPath = path.resolve(__dirname, "extension.js");
  delete require.cache[extensionPath];
  const extension = require(extensionPath);
  Module._load = originalLoad;
  const context = { subscriptions: [] };

  try {
    extension.activate(context);
    await new Promise((resolve) => setTimeout(resolve, 4_300));
    assert.ok(calls.register >= 1);
    assert.ok(calls.commands >= 2);
    assert.equal(calls.complete, 1);
    assert.ok(calls.heartbeat >= 1);
    assert.equal(calls.configured, 1);
  } finally {
    for (const subscription of context.subscriptions) subscription.dispose?.();
    await new Promise((resolve) => server.close(resolve));
    if (previousLocalAppData === undefined) delete process.env.LOCALAPPDATA;
    else process.env.LOCALAPPDATA = previousLocalAppData;
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});

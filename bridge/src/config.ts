import { existsSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

export interface BridgeConfig {
  host: string;
  port: number;
  apiToken: string | null;
  codexBin: string;
  codexProxyPath: string | null;
  requestTimeoutMs: number;
  historyTimeoutMs: number;
  maxLineBytes: number;
  maxPendingBytes: number;
  maxWsBufferedBytes: number;
  eventBufferSize: number;
  queueLimitPerThread: number;
  vscodeBindingFile: string;
  threadInstructionsFile: string;
  threadRetrySettingsFile: string;
  threadQueueFile: string;
  mcpDialogSettingsFile: string;
  filesDirectory: string;
  hostProcessId: number | null;
  ftpPort: number;
  ftpUsername: string;
  ftpPassword: string | null;
}

function extensionDirectories(): string[] {
  const userProfile = process.env.USERPROFILE;
  if (!userProfile) return [];

  const directories = [
    join(userProfile, ".vscode", "extensions"),
    join(userProfile, ".vscode-insiders", "extensions"),
    join(userProfile, ".cursor", "extensions"),
    join(userProfile, ".windsurf", "extensions"),
    join(userProfile, ".antigravity", "extensions"),
    join(userProfile, ".antigravity-ide", "extensions")
  ].filter(existsSync);

  const hostExecutable = process.env.BRIDGE_HOST_EXECUTABLE?.toLocaleLowerCase("en-US") ?? "";
  const preferredMarker = hostExecutable.includes("windsurf")
    ? ".windsurf"
    : hostExecutable.includes("cursor")
      ? ".cursor"
    : hostExecutable.includes("antigravity")
        ? ".antigravity-ide"
        : hostExecutable.includes("insiders")
          ? ".vscode-insiders"
          : ".vscode";
  return directories.sort((left, right) =>
    Number(right.toLocaleLowerCase("en-US").includes(preferredMarker)) -
    Number(left.toLocaleLowerCase("en-US").includes(preferredMarker))
  );
}

function newestExtensionCodex(): string | null {
  const candidates = extensionDirectories().flatMap((extensionsDir) =>
    readdirSync(extensionsDir)
      .filter((name) => name.startsWith("openai.chatgpt-"))
      .map((name) => {
        const binary = join(extensionsDir, name, "bin", "windows-x86_64", "codex.exe");
        return existsSync(binary) ? { binary, mtime: statSync(binary).mtimeMs, extensionsDir } : null;
      })
      .filter((candidate): candidate is { binary: string; mtime: number; extensionsDir: string } => candidate !== null)
  );
  if (candidates.length === 0) return null;

  const preferredDirectory = extensionDirectories()[0];
  candidates.sort((left, right) => {
    const preferred = Number(right.extensionsDir === preferredDirectory) - Number(left.extensionsDir === preferredDirectory);
    return preferred || right.mtime - left.mtime;
  });

  return candidates[0]?.binary ?? null;
}

function resolveCodexBin(): string {
  const configured = process.env.CODEX_BIN?.trim();
  if (configured) {
    const absolute = resolve(configured);
    if (!existsSync(absolute)) throw new Error(`CODEX_BIN does not exist: ${absolute}`);
    return absolute;
  }

  const extensionBinary = newestExtensionCodex();
  if (extensionBinary) return extensionBinary;

  throw new Error("Cannot find the VS Code Codex binary. Set CODEX_BIN explicitly.");
}

function intEnv(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

export function loadConfig(): BridgeConfig {
  const host = process.env.BRIDGE_HOST?.trim() || "127.0.0.1";
  const apiToken = process.env.BRIDGE_API_TOKEN?.trim() || null;
  const configuredProxy = process.env.BRIDGE_CODEX_PROXY?.trim();
  const codexProxyPath = configuredProxy ? resolve(configuredProxy) : null;
  if (host !== "127.0.0.1" && host !== "localhost" && host !== "::1" && !apiToken) {
    throw new Error("BRIDGE_API_TOKEN is required when BRIDGE_HOST is not loopback");
  }
  if (codexProxyPath && !existsSync(codexProxyPath)) {
    throw new Error(`BRIDGE_CODEX_PROXY does not exist: ${codexProxyPath}`);
  }

  const dataDirectory = join(
    process.env.LOCALAPPDATA || process.env.USERPROFILE || ".",
    "CodexMobileBridge"
  );
  return {
    host,
    port: intEnv("BRIDGE_PORT", 47831),
    apiToken,
    codexBin: resolveCodexBin(),
    codexProxyPath,
    requestTimeoutMs: intEnv("CODEX_REQUEST_TIMEOUT_MS", 30_000),
    historyTimeoutMs: intEnv("CODEX_HISTORY_TIMEOUT_MS", 120_000),
    maxLineBytes: intEnv("CODEX_MAX_LINE_BYTES", 96 * 1024 * 1024),
    maxPendingBytes: intEnv("CODEX_MAX_PENDING_BYTES", 128 * 1024 * 1024),
    maxWsBufferedBytes: intEnv("BRIDGE_MAX_WS_BUFFERED_BYTES", 2 * 1024 * 1024),
    eventBufferSize: intEnv("BRIDGE_EVENT_BUFFER_SIZE", 2_000),
    queueLimitPerThread: intEnv("BRIDGE_THREAD_QUEUE_LIMIT", 20),
    vscodeBindingFile: join(dataDirectory, "vscode-binding.json"),
    threadInstructionsFile: join(dataDirectory, "thread-instructions.json"),
    threadRetrySettingsFile: join(dataDirectory, "thread-retry-settings.json"),
    threadQueueFile: join(dataDirectory, "thread-queues.json"),
    mcpDialogSettingsFile: join(dataDirectory, "mcp-dialog.json"),
    filesDirectory: join(dataDirectory, "files"),
    hostProcessId: process.env.BRIDGE_HOST_PROCESS_ID
      ? intEnv("BRIDGE_HOST_PROCESS_ID", 0)
      : null,
    ftpPort: intEnv("BRIDGE_FTP_PORT", 2121),
    ftpUsername: process.env.BRIDGE_FTP_USERNAME?.trim() || "codex",
    ftpPassword: process.env.BRIDGE_FTP_PASSWORD?.trim() || null
  };
}

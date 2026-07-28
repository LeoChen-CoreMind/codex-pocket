import { randomBytes } from "node:crypto";
import { lstatSync, realpathSync } from "node:fs";
import { networkInterfaces } from "node:os";
import { isAbsolute, relative, resolve, sep } from "node:path";
import { FileSystem, FtpSrv, type FtpConnection } from "@electerm/ftp-srv";
import { WorkspaceService } from "./workspace-service.js";

interface ResolvedFtpPath {
  clientPath: string;
  fsPath: string;
}

class ContainedFileSystem extends FileSystem {
  private readonly physicalRoot: string;

  constructor(connection: FtpConnection, root: string) {
    const physicalRoot = realpathSync.native(root);
    super(connection, { root: physicalRoot, cwd: "/" });
    this.physicalRoot = physicalRoot;
  }

  // ftp-srv dispatches every filesystem operation through this internal resolver.
  _resolvePath(path = "."): ResolvedFtpPath {
    if (path.includes("\0")) throw new Error("Invalid FTP path");
    const resolvedPath = super._resolvePath(path);
    const child = relative(this.physicalRoot, resolve(resolvedPath.fsPath));
    if (child.startsWith("..") || isAbsolute(child)) throw new Error("FTP path escapes the workspace");

    let current = this.physicalRoot;
    for (const part of child.split(sep).filter(Boolean)) {
      current = resolve(current, part);
      try {
        if (lstatSync(current).isSymbolicLink()) {
          throw new Error("FTP access through symbolic links is not allowed");
        }
      } catch (error) {
        const code = (error as NodeJS.ErrnoException).code;
        if (code === "ENOENT") break;
        throw error;
      }
    }
    return resolvedPath;
  }
}

function normalizedLanAddress(value?: string | null): string | null {
  if (!value) return null;
  const address = value.replace(/^::ffff:/, "");
  if (address === "127.0.0.1" || address === "::1") return null;
  return address.includes(":") ? null : address;
}

function lanAddress(preferred?: string | null): string {
  const requestAddress = normalizedLanAddress(preferred);
  if (requestAddress) return requestAddress;
  const candidates: Array<{ address: string; interfaceName: string }> = [];
  for (const [interfaceName, entries] of Object.entries(networkInterfaces())) {
    for (const entry of entries ?? []) {
      if (entry.family === "IPv4" && !entry.internal &&
        (entry.address.startsWith("192.168.") || entry.address.startsWith("10.") || /^172\.(1[6-9]|2\d|3[01])\./.test(entry.address))) {
        candidates.push({ address: entry.address, interfaceName });
      }
    }
  }
  return candidates.sort((left, right) => lanCandidatePriority(right) - lanCandidatePriority(left))[0]?.address ?? "127.0.0.1";
}

function lanCandidatePriority(candidate: { address: string; interfaceName: string }): number {
  const name = candidate.interfaceName.toLocaleLowerCase("en-US");
  const virtual = /(virtual|vmware|vethernet|hyper-v|wsl|loopback|tap|tun|clash|zerotier)/.test(name);
  const physical = /(^|\s)(wlan|wi-?fi|ethernet)(\s|$)/.test(name);
  const addressScore = candidate.address.startsWith("192.168.") ? 30 : candidate.address.startsWith("10.") ? 20 : 10;
  return addressScore + (physical ? 100 : 0) - (virtual ? 100 : 0);
}

export class WorkspaceFtpService {
  private server: FtpSrv | null = null;
  private port = 0;
  private username: string;
  private password: string;
  private advertisedHost: string | null = null;

  constructor(
    private readonly workspace: WorkspaceService,
    initial: { username?: string; password?: string | null } = {}
  ) {
    this.username = initial.username?.trim() || "codex";
    this.password = initial.password?.trim() || randomBytes(12).toString("base64url");
  }

  status(preferredHost?: string | null) {
    this.advertisedHost = normalizedLanAddress(preferredHost) ?? this.advertisedHost;
    let root: string | null = null;
    try {
      root = this.workspace.roots()[0]?.path ?? null;
    } catch {
      // Status remains readable while no editor window is bound; start() still requires a root.
    }
    return {
      running: this.server !== null,
      host: this.advertisedHost ?? lanAddress(),
      port: this.port,
      username: this.username,
      password: this.password,
      root,
      connections: this.server ? Object.keys(this.server.connections).length : 0
    };
  }

  async start(
    port = 2121,
    preferredHost?: string | null,
    credentials?: { username?: string; password?: string }
  ) {
    this.advertisedHost = normalizedLanAddress(preferredHost) ?? this.advertisedHost;
    if (this.server) return this.status();
    const username = credentials?.username?.trim();
    const password = credentials?.password?.trim();
    if (username) this.username = username;
    if (password) this.password = password;
    const root = this.workspace.roots()[0]?.path;
    if (!root) throw new Error("Bind an online workspace before starting FTP");
    const host = this.advertisedHost ?? lanAddress();
    const server = new FtpSrv({
      url: `ftp://0.0.0.0:${port}`,
      pasv_url: host,
      pasv_min: 50_000,
      pasv_max: 50_100,
      anonymous: false,
      greeting: "Codex Pocket workspace FTP",
      timeout: 5 * 60_000,
      endOnProcessSignal: false,
      log: { trace() {}, debug() {}, info() {}, warn() {}, error() {}, fatal() {}, child() { return this; } }
    });
    server.server.maxConnections = 16;
    server.on("login", (
      data: { connection: FtpConnection; username: string; password: string },
      resolveLogin: (config: { fs: FileSystem }) => void,
      rejectLogin: (error?: Error) => void
    ) => {
      if (data.username === this.username && data.password === this.password) {
        resolveLogin({ fs: new ContainedFileSystem(data.connection, root) });
      } else {
        rejectLogin(new Error("Invalid FTP credentials"));
      }
    });
    try {
      await server.listen();
      this.server = server;
      this.port = port;
      return this.status();
    } catch (error) {
      await server.close().catch(() => undefined);
      throw error;
    }
  }

  async stop() {
    const server = this.server;
    this.server = null;
    this.port = 0;
    if (server) {
      await Promise.allSettled(
        Object.values(server.connections).map((connection) => connection.close(421, "FTP service stopped"))
      );
      await server.close();
    }
    return this.status();
  }
}

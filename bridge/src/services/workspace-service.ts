import { createHash } from "node:crypto";
import { createReadStream, realpathSync } from "node:fs";
import { readdir, readFile, stat } from "node:fs/promises";
import { basename, extname, isAbsolute, relative, resolve } from "node:path";
import { VsCodeInstanceService } from "./vscode-instance-service.js";

const MAX_TEXT_BYTES = 2 * 1024 * 1024;

export interface WorkspaceRoot {
  id: string;
  name: string;
  path: string;
}

function rootId(path: string): string {
  return createHash("sha256").update(resolve(path).toLocaleLowerCase("en-US")).digest("hex").slice(0, 24);
}

export function workspaceRootForPath(path: string): WorkspaceRoot {
  const absolute = resolve(path);
  return {
    id: rootId(absolute),
    name: basename(absolute) || absolute,
    path: absolute
  };
}

function mimeType(path: string): string {
  const extension = extname(path).toLowerCase();
  return ({
    ".kt": "text/x-kotlin", ".kts": "text/x-kotlin", ".java": "text/x-java",
    ".ts": "text/typescript", ".tsx": "text/typescript", ".js": "text/javascript", ".jsx": "text/javascript",
    ".json": "application/json", ".xml": "application/xml", ".html": "text/html", ".css": "text/css",
    ".md": "text/markdown", ".txt": "text/plain", ".yml": "text/yaml", ".yaml": "text/yaml",
    ".py": "text/x-python", ".go": "text/x-go", ".rs": "text/x-rust", ".c": "text/x-c", ".cpp": "text/x-c++",
    ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp",
    ".pdf": "application/pdf", ".zip": "application/zip"
  } as Record<string, string>)[extension] ?? "application/octet-stream";
}

export class WorkspaceService {
  constructor(private readonly vscode: VsCodeInstanceService) {}

  roots(): WorkspaceRoot[] {
    return this.vscode.requireBoundInstance().workspaceFolders.map(workspaceRootForPath);
  }

  rootById(id: string): WorkspaceRoot | null {
    return this.roots().find((root) => root.id === id) ?? null;
  }

  projectForPath(path: string): WorkspaceRoot | null {
    const candidate = resolve(path);
    return this.roots().find((root) => this.contains(root.path, candidate)) ?? null;
  }

  async list(rootIdValue: string, relativePath: string): Promise<unknown> {
    const { root, absolute, relativePath: normalized } = this.resolvePath(rootIdValue, relativePath);
    const entries = await readdir(absolute, { withFileTypes: true });
    const data = await Promise.all(entries.map(async (entry) => {
      const child = resolve(absolute, entry.name);
      const childStat = await stat(child).catch(() => null);
      const childRelative = relative(root.path, child).replaceAll("\\", "/");
      return {
        name: entry.name,
        path: childRelative,
        isDirectory: entry.isDirectory(),
        size: childStat?.isFile() ? childStat.size : null,
        modifiedAt: childStat?.mtime.toISOString() ?? null,
        type: entry.isDirectory() ? "inode/directory" : mimeType(child)
      };
    }));
    data.sort((left, right) => Number(right.isDirectory) - Number(left.isDirectory) || left.name.localeCompare(right.name));
    return { root, path: normalized, data };
  }

  async readText(rootIdValue: string, relativePath: string): Promise<unknown> {
    const { root, absolute, relativePath: normalized } = this.resolvePath(rootIdValue, relativePath);
    const info = await stat(absolute);
    if (!info.isFile()) throw new Error("Path is not a file");
    if (info.size > MAX_TEXT_BYTES) throw new Error("File is too large to preview");
    const type = mimeType(absolute);
    if (!type.startsWith("text/") && !["application/json", "application/xml"].includes(type)) {
      return { root, path: normalized, type, size: info.size, binary: true, content: null };
    }
    return {
      root,
      path: normalized,
      type,
      size: info.size,
      binary: false,
      content: await readFile(absolute, "utf8")
    };
  }

  raw(rootIdValue: string, relativePath: string) {
    const { absolute } = this.resolvePath(rootIdValue, relativePath);
    return { type: mimeType(absolute), stream: createReadStream(absolute) };
  }

  absolutePath(rootIdValue: string, relativePath: string): string {
    return this.resolvePath(rootIdValue, relativePath).absolute;
  }

  private resolvePath(rootIdValue: string, requestedPath: string) {
    const root = this.rootById(rootIdValue);
    if (!root) throw new Error("Unknown workspace root");
    if (requestedPath.includes("\0") || isAbsolute(requestedPath)) throw new Error("Invalid workspace path");
    const absolute = resolve(root.path, requestedPath || ".");
    if (!this.contains(root.path, absolute)) throw new Error("Path escapes the workspace root");
    const physicalRoot = realpathSync.native(root.path);
    const physicalPath = realpathSync.native(absolute);
    if (!this.contains(physicalRoot, physicalPath)) throw new Error("Path escapes the workspace root through a link");
    return {
      root,
      absolute: physicalPath,
      relativePath: relative(root.path, absolute).replaceAll("\\", "/")
    };
  }

  private contains(root: string, candidate: string): boolean {
    const child = relative(resolve(root), resolve(candidate));
    return child === "" || (!child.startsWith("..") && !isAbsolute(child));
  }
}

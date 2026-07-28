import { createHash } from "node:crypto";
import { lstatSync } from "node:fs";
import { mkdir, opendir, readFile, realpath, rename, rm, stat, unlink, writeFile } from "node:fs/promises";
import { basename, dirname, extname, isAbsolute, relative, resolve, sep } from "node:path";
import { WorkspaceService, type WorkspaceRoot } from "./workspace-service.js";

const SKILL_FILE = "SKILL.md";
const MAX_SCAN_DEPTH = 12;
const MAX_SCAN_DIRECTORIES = 12_000;
const MAX_SKILLS = 1_000;
const MAX_IMPORT_BYTES = 50 * 1024 * 1024;
const CACHE_TTL_MS = 10_000;
const IGNORED_DIRECTORIES = new Set([
  ".git", ".gradle", ".idea", ".next", ".nuxt", ".svn", ".vscode",
  "build", "coverage", "dist", "node_modules", "out", "target"
]);

export interface SkillPayload {
  name?: string;
  displayTitle?: string | null;
  description?: string;
  body?: string;
  category?: string | null;
  frontmatter?: Record<string, unknown> | null;
  alwaysApply?: boolean | null;
  expectedVersion?: number;
}

interface SkillRecord {
  id: string;
  root: WorkspaceRoot;
  directory: string;
  relativeDirectory: string;
  skillFile: string;
}

interface ParsedSkill {
  frontmatter: Record<string, unknown>;
  body: string;
}

function idFor(rootId: string, relativeDirectory: string): string {
  return createHash("sha256")
    .update(`${rootId}:${relativeDirectory.toLocaleLowerCase("en-US")}`)
    .digest("hex")
    .slice(0, 24);
}

function isSkillLocation(path: string): boolean {
  return path.split("/").some((part) => part.toLocaleLowerCase("en-US") === "skills");
}

function cleanName(value: string): string {
  const name = value.trim().toLocaleLowerCase("en-US");
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(name)) {
    throw new Error("Skill name must use lowercase letters, numbers, and hyphens");
  }
  return name;
}

function parseSkillMarkdown(content: string): ParsedSkill {
  const normalized = content.replaceAll("\r\n", "\n");
  const match = /^---\n([\s\S]*?)\n---(?:\n|$)([\s\S]*)$/.exec(normalized);
  if (!match) return { frontmatter: {}, body: normalized.trim() };
  return {
    frontmatter: parseFrontmatter(match[1] ?? ""),
    body: (match[2] ?? "").trim()
  };
}

function parseFrontmatter(source: string): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  let listKey: string | null = null;
  for (const rawLine of source.split("\n")) {
    const list = /^\s*-\s+(.+)$/.exec(rawLine);
    if (list && listKey) {
      const values = Array.isArray(result[listKey]) ? result[listKey] as unknown[] : [];
      values.push(parseScalar(list[1] ?? ""));
      result[listKey] = values;
      continue;
    }
    const pair = /^([A-Za-z0-9_.-]+):(?:\s*(.*))?$/.exec(rawLine);
    if (!pair) continue;
    const key = pair[1]!;
    const value = pair[2]?.trim() ?? "";
    if (!value) {
      result[key] = [];
      listKey = key;
    } else {
      result[key] = parseScalar(value);
      listKey = null;
    }
  }
  return result;
}

function parseScalar(value: string): unknown {
  if (value === "true") return true;
  if (value === "false") return false;
  if (value === "null" || value === "~") return null;
  if (/^-?\d+(?:\.\d+)?$/.test(value)) return Number(value);
  if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
    if (value.startsWith("\"")) return JSON.parse(value);
    return value.slice(1, -1).replaceAll("''", "'");
  }
  if (value.startsWith("[") || value.startsWith("{")) {
    try { return JSON.parse(value); } catch { /* keep as text */ }
  }
  return value.replace(/\s+#.*$/, "").trim();
}

function stringifyFrontmatter(frontmatter: Record<string, unknown>): string {
  return Object.entries(frontmatter).flatMap(([key, value]) => {
    if (Array.isArray(value)) return [`${key}:`, ...value.map((item) => `  - ${scalarText(item)}`)];
    return [`${key}: ${scalarText(value)}`];
  }).join("\n");
}

function scalarText(value: unknown): string {
  if (typeof value === "boolean" || typeof value === "number") return String(value);
  if (value == null) return "null";
  if (typeof value === "object") return JSON.stringify(value);
  const text = String(value);
  return /^[A-Za-z0-9_./ -]+$/.test(text) && !/^(true|false|null|~)$/i.test(text)
    ? text
    : JSON.stringify(text);
}

function skillMarkdown(payload: SkillPayload, current: ParsedSkill | null): string {
  const frontmatter = { ...(current?.frontmatter ?? {}), ...(payload.frontmatter ?? {}) };
  const name = cleanName(payload.name ?? String(frontmatter.name ?? ""));
  frontmatter.name = name;
  frontmatter.description = payload.description ?? String(frontmatter.description ?? "");
  const displayTitle = payload.displayTitle ?? frontmatter["display-title"];
  if (typeof displayTitle === "string" && displayTitle.trim()) frontmatter["display-title"] = displayTitle.trim();
  else delete frontmatter["display-title"];
  const category = payload.category ?? frontmatter.category;
  if (typeof category === "string" && category.trim()) frontmatter.category = category.trim();
  else delete frontmatter.category;
  if (payload.alwaysApply != null) frontmatter["always-apply"] = payload.alwaysApply;
  return `---\n${stringifyFrontmatter(frontmatter).trim()}\n---\n\n${payload.body ?? current?.body ?? ""}`.trimEnd() + "\n";
}

function mimeType(path: string): string {
  return ({
    ".md": "text/markdown", ".txt": "text/plain", ".json": "application/json",
    ".yaml": "text/yaml", ".yml": "text/yaml", ".js": "text/javascript",
    ".ts": "text/typescript", ".kt": "text/x-kotlin", ".java": "text/x-java",
    ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
    ".gif": "image/gif", ".webp": "image/webp", ".pdf": "application/pdf"
  } as Record<string, string>)[extname(path).toLocaleLowerCase("en-US")] ?? "application/octet-stream";
}

export class WorkspaceSkillService {
  private cache: { expiresAt: number; records: SkillRecord[] } | null = null;
  private scanning: Promise<SkillRecord[]> | null = null;
  private readonly activeStates = new Map<string, boolean>();

  constructor(private readonly workspace: WorkspaceService) {}

  async list(input: { limit: number; search: string | null; cursor: string | null }) {
    const records = await this.records();
    const query = input.search?.toLocaleLowerCase("en-US") ?? null;
    const skills = await Promise.all(records.map((record) => this.serialize(record, false)));
    const filtered = query
      ? skills.filter((skill) => `${skill.name} ${skill.displayTitle ?? ""} ${skill.description} ${skill.source}`
          .toLocaleLowerCase("en-US").includes(query))
      : skills;
    const offset = Math.max(0, Number.parseInt(input.cursor ?? "0", 10) || 0);
    const page = filtered.slice(offset, offset + input.limit);
    const next = offset + page.length;
    return { skills: page, has_more: next < filtered.length, after: next < filtered.length ? String(next) : null };
  }

  async get(id: string) {
    return this.serialize(await this.requireRecord(id), true);
  }

  async create(payload: SkillPayload) {
    const name = cleanName(payload.name ?? "");
    const root = this.workspace.roots()[0];
    if (!root) throw new Error("No bound workspace root");
    const directory = resolve(root.path, ".agents", "skills", name);
    this.assertContained(root.path, directory);
    await mkdir(directory, { recursive: false }).catch((error: NodeJS.ErrnoException) => {
      if (error.code === "ENOENT") return mkdir(dirname(directory), { recursive: true }).then(() => mkdir(directory));
      throw error;
    });
    await writeFile(resolve(directory, SKILL_FILE), skillMarkdown({ ...payload, name }, null), "utf8");
    this.invalidate();
    return this.get(idFor(root.id, relative(root.path, directory).replaceAll("\\", "/")));
  }

  async update(id: string, payload: SkillPayload) {
    let record = await this.requireRecord(id);
    const currentStat = await stat(record.skillFile);
    const version = this.version(currentStat.mtimeMs);
    if (payload.expectedVersion != null && payload.expectedVersion !== version) {
      return { conflict: true, current: await this.serialize(record, true) };
    }
    const current = parseSkillMarkdown(await readFile(record.skillFile, "utf8"));
    const nextName = cleanName(payload.name ?? String(current.frontmatter.name ?? basename(record.directory)));
    if (nextName !== basename(record.directory).toLocaleLowerCase("en-US")) {
      const nextDirectory = resolve(dirname(record.directory), nextName);
      this.assertContained(record.root.path, nextDirectory);
      await rename(record.directory, nextDirectory);
      record = {
        ...record,
        id: idFor(record.root.id, relative(record.root.path, nextDirectory).replaceAll("\\", "/")),
        directory: nextDirectory,
        relativeDirectory: relative(record.root.path, nextDirectory).replaceAll("\\", "/"),
        skillFile: resolve(nextDirectory, SKILL_FILE)
      };
    }
    await writeFile(record.skillFile, skillMarkdown({ ...payload, name: nextName }, current), "utf8");
    this.invalidate();
    return { conflict: false, skill: await this.serialize(record, true) };
  }

  async delete(id: string): Promise<void> {
    const record = await this.requireRecord(id);
    this.assertContained(record.root.path, record.directory);
    await rm(record.directory, { recursive: true, force: false });
    this.activeStates.delete(id);
    this.invalidate();
  }

  async import(filename: string, bytes: Buffer) {
    const root = this.workspace.roots()[0];
    if (!root) throw new Error("No bound workspace root");
    let entries: Record<string, Uint8Array>;
    const extension = extname(filename).toLocaleLowerCase("en-US");
    if (extension === ".skill") {
      const archive = JSON.parse(bytes.toString("utf8")) as {
        format?: string;
        files?: Record<string, string>;
      };
      if (archive.format !== "codex-pocket-skill-v1" || !archive.files) throw new Error("Invalid .skill archive");
      entries = Object.fromEntries(
        Object.entries(archive.files).map(([path, encoded]) => [path, Buffer.from(encoded, "base64")])
      );
    } else if (extension === ".zip") {
      throw new Error("ZIP import is unavailable on this Bridge build; use .md or .skill");
    } else {
      entries = { [SKILL_FILE]: bytes };
    }
    const files = Object.entries(entries).filter(([, value]) => value.length > 0);
    const total = files.reduce((sum, [, value]) => sum + value.length, 0);
    if (total > MAX_IMPORT_BYTES) throw new Error("Imported skill is too large");
    const skillEntry = files.find(([path]) => path.replaceAll("\\", "/").endsWith(`/${SKILL_FILE}`))
      ?? files.find(([path]) => basename(path) === SKILL_FILE);
    if (!skillEntry) throw new Error("Imported file does not contain SKILL.md");
    const parsed = parseSkillMarkdown(Buffer.from(skillEntry[1]).toString("utf8"));
    const name = cleanName(String(parsed.frontmatter.name ?? basename(filename, extname(filename))));
    const target = resolve(root.path, ".agents", "skills", name);
    this.assertContained(root.path, target);
    await mkdir(target, { recursive: false }).catch((error: NodeJS.ErrnoException) => {
      if (error.code === "ENOENT") return mkdir(dirname(target), { recursive: true }).then(() => mkdir(target));
      throw error;
    });
    const prefix = skillEntry[0].slice(0, Math.max(0, skillEntry[0].length - SKILL_FILE.length));
    for (const [entryPath, value] of files) {
      const normalized = entryPath.replaceAll("\\", "/");
      const relativePath = normalized.startsWith(prefix) ? normalized.slice(prefix.length) : normalized;
      const pathParts = relativePath.split("/");
      if (!relativePath || relativePath.includes("\0") || relativePath.endsWith("/") ||
        isAbsolute(relativePath) || pathParts.some((part) => part === "..")) continue;
      const output = resolve(target, relativePath);
      this.assertContained(target, output);
      this.assertNoLinkTraversal(target, output);
      await mkdir(dirname(output), { recursive: true });
      await writeFile(output, value);
    }
    this.invalidate();
    return this.get(idFor(root.id, relative(root.path, target).replaceAll("\\", "/")));
  }

  async export(id: string): Promise<{ filename: string; bytes: Buffer }> {
    const record = await this.requireRecord(id);
    const files = await this.skillFiles(record, true);
    const archive: Record<string, string> = {};
    for (const file of files) archive[file.relativePath] = (await readFile(file.absolute)).toString("base64");
    archive[SKILL_FILE] = (await readFile(record.skillFile)).toString("base64");
    return {
      filename: `${basename(record.directory)}.skill`,
      bytes: Buffer.from(JSON.stringify({ format: "codex-pocket-skill-v1", files: archive }), "utf8")
    };
  }

  states(): Record<string, boolean> {
    return Object.fromEntries(this.activeStates);
  }

  setStates(states: Record<string, boolean>): Record<string, boolean> {
    this.activeStates.clear();
    for (const [id, active] of Object.entries(states)) if (typeof active === "boolean") this.activeStates.set(id, active);
    return this.states();
  }

  async listFiles(id: string) {
    const record = await this.requireRecord(id);
    return Promise.all((await this.skillFiles(record, false)).map(async (file) => this.fileDto(record, file)));
  }

  async readFile(id: string, relativePath: string) {
    const record = await this.requireRecord(id);
    const absolute = this.resolveSkillFile(record, relativePath);
    const info = await stat(absolute);
    const type = mimeType(absolute);
    const binary = !type.startsWith("text/") && !["application/json", "application/xml"].includes(type);
    return {
      relativePath,
      filename: basename(relativePath),
      mimeType: type,
      bytes: info.size,
      isBinary: binary,
      content: binary ? undefined : await readFile(absolute, "utf8")
    };
  }

  async writeFile(id: string, relativePath: string, bytes: Buffer) {
    const record = await this.requireRecord(id);
    const absolute = this.resolveSkillFile(record, relativePath);
    if (basename(absolute).toLocaleLowerCase("en-US") === SKILL_FILE.toLocaleLowerCase("en-US")) {
      throw new Error("SKILL.md must be edited through the skill editor");
    }
    await mkdir(dirname(absolute), { recursive: true });
    await writeFile(absolute, bytes);
    this.invalidate();
    return this.fileDto(record, { relativePath, absolute });
  }

  async deleteFile(id: string, relativePath: string): Promise<void> {
    const record = await this.requireRecord(id);
    const absolute = this.resolveSkillFile(record, relativePath);
    if (basename(absolute).toLocaleLowerCase("en-US") === SKILL_FILE.toLocaleLowerCase("en-US")) {
      throw new Error("SKILL.md cannot be deleted separately");
    }
    await unlink(absolute);
    this.invalidate();
  }

  private async serialize(record: SkillRecord, includeBody: boolean) {
    const content = await readFile(record.skillFile, "utf8");
    const parsed = parseSkillMarkdown(content);
    const info = await stat(record.skillFile);
    const name = String(parsed.frontmatter.name ?? basename(record.directory));
    const files = await this.skillFiles(record, false);
    return {
      _id: record.id,
      name,
      displayTitle: typeof parsed.frontmatter["display-title"] === "string"
        ? parsed.frontmatter["display-title"]
        : null,
      description: String(parsed.frontmatter.description ?? ""),
      ...(includeBody ? { body: parsed.body, frontmatter: parsed.frontmatter } : {}),
      category: typeof parsed.frontmatter.category === "string" ? parsed.frontmatter.category : null,
      alwaysApply: parsed.frontmatter["always-apply"] === true,
      version: this.version(info.mtimeMs),
      source: `${record.root.name}/${record.relativeDirectory}`,
      fileCount: files.length,
      createdAt: info.birthtime.toISOString(),
      updatedAt: info.mtime.toISOString()
    };
  }

  private version(mtimeMs: number): number {
    return Math.max(1, Math.min(2_147_483_647, Math.floor(mtimeMs / 1000)));
  }

  private async records(): Promise<SkillRecord[]> {
    if (this.cache && this.cache.expiresAt > Date.now()) return this.cache.records;
    if (this.scanning) return this.scanning;
    this.scanning = this.scan().finally(() => { this.scanning = null; });
    const records = await this.scanning;
    this.cache = { expiresAt: Date.now() + CACHE_TTL_MS, records };
    return records;
  }

  private async scan(): Promise<SkillRecord[]> {
    const result: SkillRecord[] = [];
    for (const root of this.workspace.roots()) {
      const queue: Array<{ path: string; depth: number }> = [{ path: root.path, depth: 0 }];
      let visited = 0;
      while (queue.length > 0 && visited < MAX_SCAN_DIRECTORIES && result.length < MAX_SKILLS) {
        const batch = queue.splice(0, 32);
        const directories = await Promise.all(batch.map(async (item) => {
          visited += 1;
          const children: Array<{ path: string; depth: number }> = [];
          try {
            const directory = await opendir(item.path);
            let hasSkill = false;
            for await (const entry of directory) {
              if (entry.isFile() && entry.name.toLocaleLowerCase("en-US") === "skill.md") hasSkill = true;
              if (entry.isDirectory() && item.depth < MAX_SCAN_DEPTH && !IGNORED_DIRECTORIES.has(entry.name)) {
                children.push({ path: resolve(item.path, entry.name), depth: item.depth + 1 });
              }
            }
            if (hasSkill) {
              const relativeDirectory = relative(root.path, item.path).replaceAll("\\", "/");
              if (isSkillLocation(relativeDirectory)) {
                const directoryPath = await realpath(item.path);
                result.push({
                  id: idFor(root.id, relativeDirectory), root, directory: directoryPath,
                  relativeDirectory, skillFile: resolve(directoryPath, SKILL_FILE)
                });
              }
            }
          } catch { /* unreadable directories are skipped */ }
          return children;
        }));
        queue.push(...directories.flat());
      }
    }
    return result.sort((left, right) => left.relativeDirectory.localeCompare(right.relativeDirectory));
  }

  private async requireRecord(id: string): Promise<SkillRecord> {
    const record = (await this.records()).find((candidate) => candidate.id === id);
    if (!record) throw new Error("Skill not found");
    return record;
  }

  private async skillFiles(record: SkillRecord, includeDirectories: boolean) {
    const result: Array<{ relativePath: string; absolute: string }> = [];
    const queue = [record.directory];
    while (queue.length > 0 && result.length < 2_000) {
      const current = queue.shift()!;
      const directory = await opendir(current);
      for await (const entry of directory) {
        const absolute = resolve(current, entry.name);
        if (entry.isDirectory()) queue.push(absolute);
        else if (entry.isFile() && entry.name.toLocaleLowerCase("en-US") !== "skill.md") {
          result.push({ relativePath: relative(record.directory, absolute).replaceAll("\\", "/"), absolute });
        }
      }
    }
    return includeDirectories ? result : result;
  }

  private async fileDto(record: SkillRecord, file: { relativePath: string; absolute: string }) {
    const info = await stat(file.absolute);
    return {
      _id: createHash("sha256").update(`${record.id}:${file.relativePath}`).digest("hex").slice(0, 24),
      skillId: record.id,
      relativePath: file.relativePath,
      filename: basename(file.relativePath),
      filepath: file.absolute,
      source: "workspace",
      mimeType: mimeType(file.absolute),
      bytes: info.size,
      createdAt: info.birthtime.toISOString(),
      updatedAt: info.mtime.toISOString()
    };
  }

  private resolveSkillFile(record: SkillRecord, requestedPath: string): string {
    if (!requestedPath || requestedPath.includes("\0") || isAbsolute(requestedPath)) throw new Error("Invalid skill file path");
    const absolute = resolve(record.directory, requestedPath);
    this.assertContained(record.directory, absolute);
    this.assertNoLinkTraversal(record.directory, absolute);
    return absolute;
  }

  private assertContained(root: string, candidate: string): void {
    const child = relative(resolve(root), resolve(candidate));
    if (child.startsWith("..") || isAbsolute(child)) throw new Error("Path escapes the workspace");
  }

  private assertNoLinkTraversal(root: string, candidate: string): void {
    const child = relative(resolve(root), resolve(candidate));
    let current = resolve(root);
    for (const part of child.split(sep).filter(Boolean)) {
      current = resolve(current, part);
      try {
        if (lstatSync(current).isSymbolicLink()) throw new Error("Skill file path cannot use symbolic links");
      } catch (error) {
        const code = (error as NodeJS.ErrnoException).code;
        if (code === "ENOENT") break;
        throw error;
      }
    }
  }

  private invalidate(): void {
    this.cache = null;
  }
}

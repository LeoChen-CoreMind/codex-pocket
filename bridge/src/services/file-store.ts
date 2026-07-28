import { createReadStream, createWriteStream } from "node:fs";
import { copyFile, mkdir, readFile, rename, stat, unlink, writeFile } from "node:fs/promises";
import { basename, extname, join, resolve } from "node:path";
import { randomUUID } from "node:crypto";
import { pipeline } from "node:stream/promises";
import type { MultipartFile } from "@fastify/multipart";
import type { BridgeFileReference } from "../types.js";

interface StoredFile extends BridgeFileReference {
  diskPath: string;
  createdAt: string;
  sourcePath: string | null;
}

const MAX_FILE_BYTES = 20 * 1024 * 1024;
const IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp", "image/gif"]);

function fileMimeType(path: string): string {
  const extension = extname(path).toLowerCase();
  return ({
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".gif": "image/gif",
    ".kt": "text/x-kotlin", ".kts": "text/x-kotlin", ".java": "text/x-java",
    ".ts": "text/typescript", ".tsx": "text/typescript", ".js": "text/javascript", ".jsx": "text/javascript",
    ".json": "application/json", ".xml": "application/xml", ".html": "text/html", ".css": "text/css",
    ".md": "text/markdown", ".txt": "text/plain", ".yml": "text/yaml", ".yaml": "text/yaml",
    ".py": "text/x-python", ".go": "text/x-go", ".rs": "text/x-rust", ".c": "text/x-c", ".cpp": "text/x-c++",
    ".pdf": "application/pdf", ".zip": "application/zip"
  } as Record<string, string>)[extension] ?? "application/octet-stream";
}

function safeExtension(filename: string, mime: string): string {
  const extension = extname(filename).toLowerCase();
  if (/^\.[a-z0-9]{1,8}$/.test(extension)) return extension;
  return ({
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif"
  } as Record<string, string>)[mime] ?? ".bin";
}

function publicFile(file: StoredFile): BridgeFileReference {
  const { diskPath: _diskPath, createdAt: _createdAt, sourcePath: _sourcePath, ...result } = file;
  return result;
}

export class FileStore {
  private readonly indexPath: string;
  private readonly files = new Map<string, StoredFile>();
  private writeChain: Promise<void> = Promise.resolve();

  constructor(private readonly directory: string) {
    this.indexPath = join(directory, "index.json");
  }

  async initialize(): Promise<void> {
    await mkdir(this.directory, { recursive: true });
    try {
      const values = JSON.parse(await readFile(this.indexPath, "utf8")) as StoredFile[];
      for (const file of values) this.files.set(file.fileId, file);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
    }
  }

  config() {
    const supportedMimeTypes = [...IMAGE_TYPES];
    const endpoint = { disabled: false, fileLimit: 10, fileSizeLimit: MAX_FILE_BYTES, supportedMimeTypes };
    return {
      fileLimit: 10,
      fileSizeLimit: MAX_FILE_BYTES,
      totalSizeLimit: 50 * 1024 * 1024,
      supportedMimeTypes,
      disabled: false,
      endpoints: { default: endpoint, openAI: endpoint }
    };
  }

  list(): BridgeFileReference[] {
    return [...this.files.values()].map(publicFile);
  }

  get(fileId: string): StoredFile | null {
    return this.files.get(fileId) ?? null;
  }

  localPath(fileId: string): string {
    const file = this.files.get(fileId);
    if (!file) throw new Error(`Unknown file: ${fileId}`);
    return file.diskPath;
  }

  inputPath(fileId: string): string {
    const file = this.files.get(fileId);
    if (!file) throw new Error(`Unknown file: ${fileId}`);
    return file.sourcePath ?? file.diskPath;
  }

  stream(fileId: string) {
    const file = this.files.get(fileId);
    if (!file) throw new Error(`Unknown file: ${fileId}`);
    return { file: publicFile(file), stream: createReadStream(file.diskPath) };
  }

  async saveUpload(part: MultipartFile): Promise<BridgeFileReference> {
    if (!IMAGE_TYPES.has(part.mimetype)) throw new Error(`Unsupported image type: ${part.mimetype}`);
    const fileId = randomUUID();
    const filename = basename(part.filename || `image${safeExtension("", part.mimetype)}`);
    const diskPath = join(this.directory, `${fileId}${safeExtension(filename, part.mimetype)}`);
    await pipeline(part.file, createWriteStream(diskPath, { flags: "wx" }));
    if (part.file.truncated) {
      await unlink(diskPath).catch(() => undefined);
      throw new Error(`File exceeds ${MAX_FILE_BYTES} bytes`);
    }
    const info = await stat(diskPath);
    const fields = part.fields as Record<string, { value?: unknown }>;
    const width = Number(fields.width?.value);
    const height = Number(fields.height?.value);
    const stored: StoredFile = {
      fileId,
      filename,
      filepath: `/api/files/${fileId}`,
      type: part.mimetype,
      bytes: info.size,
      width: Number.isInteger(width) && width > 0 ? width : null,
      height: Number.isInteger(height) && height > 0 ? height : null,
      diskPath,
      createdAt: new Date().toISOString(),
      sourcePath: null
    };
    this.files.set(fileId, stored);
    await this.persist();
    return publicFile(stored);
  }

  async importLocalImage(sourcePath: string): Promise<BridgeFileReference | null> {
    if (!IMAGE_TYPES.has(fileMimeType(sourcePath))) return null;
    return this.importLocalFile(sourcePath).catch(() => null);
  }

  async importLocalFile(sourcePath: string): Promise<BridgeFileReference> {
    const absolute = resolve(sourcePath);
    for (const existing of this.files.values()) {
      if (resolve(existing.diskPath) === absolute || (existing.sourcePath && resolve(existing.sourcePath) === absolute)) {
        return publicFile(existing);
      }
    }
    let info;
    try {
      info = await stat(absolute);
    } catch {
      throw new Error("Workspace file is unavailable");
    }
    if (!info.isFile()) throw new Error("Path is not a file");
    if (info.size > MAX_FILE_BYTES) throw new Error(`File exceeds ${MAX_FILE_BYTES} bytes`);
    const extension = extname(absolute).toLowerCase();
    const type = fileMimeType(absolute);
    const fileId = randomUUID();
    const filename = basename(absolute);
    const diskPath = join(this.directory, `${fileId}${extension}`);
    await copyFile(absolute, diskPath);
    const stored: StoredFile = {
      fileId,
      filename,
      filepath: `/api/files/${fileId}`,
      type,
      bytes: info.size,
      width: null,
      height: null,
      diskPath,
      createdAt: new Date().toISOString(),
      sourcePath: absolute
    };
    this.files.set(fileId, stored);
    await this.persist();
    return publicFile(stored);
  }

  async delete(fileIds: string[]): Promise<void> {
    for (const fileId of fileIds) {
      const file = this.files.get(fileId);
      if (!file) continue;
      this.files.delete(fileId);
      await unlink(file.diskPath).catch(() => undefined);
    }
    await this.persist();
  }

  private persist(): Promise<void> {
    this.writeChain = this.writeChain.then(async () => {
      const temporary = `${this.indexPath}.${process.pid}.tmp`;
      await writeFile(temporary, JSON.stringify([...this.files.values()], null, 2), "utf8");
      await rename(temporary, this.indexPath);
    });
    return this.writeChain;
  }
}

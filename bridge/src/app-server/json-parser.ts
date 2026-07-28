import { Worker } from "node:worker_threads";
import { dirname, join } from "node:path";

interface PendingParse {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
}

export class JsonParser implements AsyncDisposable {
  private readonly worker: Worker;
  private readonly pending = new Map<number, PendingParse>();
  private nextId = 1;
  private closed = false;

  constructor(private readonly workerThresholdBytes = 256 * 1024) {
    const configuredWorker = process.env.BRIDGE_JSON_WORKER?.trim();
    const entryDirectory = dirname(process.argv[1] ?? process.cwd());
    const workerPath = configuredWorker || join(entryDirectory, "app-server", "json-parser.worker.js");
    this.worker = new Worker(workerPath);
    this.worker.on("message", (message: { id: number; value?: unknown; error?: string }) => {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(message.error));
      else pending.resolve(message.value);
    });
    this.worker.on("error", (error) => this.rejectAll(error));
    this.worker.on("exit", (code) => {
      if (!this.closed && code !== 0) this.rejectAll(new Error(`JSON parser worker exited: ${code}`));
    });
  }

  parse(text: string): Promise<unknown> {
    if (this.closed) return Promise.reject(new Error("JSON parser is closed"));
    if (Buffer.byteLength(text) < this.workerThresholdBytes) {
      try {
        return Promise.resolve(JSON.parse(text));
      } catch (error) {
        return Promise.reject(error);
      }
    }

    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.worker.postMessage({ id, text });
    });
  }

  private rejectAll(error: Error): void {
    for (const pending of this.pending.values()) pending.reject(error);
    this.pending.clear();
  }

  async [Symbol.asyncDispose](): Promise<void> {
    this.closed = true;
    this.rejectAll(new Error("JSON parser closed"));
    await this.worker.terminate();
  }
}

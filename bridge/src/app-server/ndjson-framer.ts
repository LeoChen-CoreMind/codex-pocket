export class NdjsonFramer {
  private buffered: Buffer<ArrayBufferLike> = Buffer.alloc(0);

  constructor(
    private readonly maxLineBytes: number,
    private readonly onLine: (line: string, bytes: number) => void
  ) {}

  push(chunk: Buffer): void {
    this.buffered = this.buffered.length === 0 ? chunk : Buffer.concat([this.buffered, chunk]);
    if (this.buffered.length > this.maxLineBytes && !this.buffered.includes(0x0a)) {
      throw new Error(`app-server line exceeds ${this.maxLineBytes} bytes`);
    }

    let newlineIndex: number;
    while ((newlineIndex = this.buffered.indexOf(0x0a)) >= 0) {
      const line = this.buffered.subarray(0, newlineIndex);
      this.buffered = this.buffered.subarray(newlineIndex + 1);
      if (line.length === 0) continue;
      if (line.length > this.maxLineBytes) throw new Error(`app-server line exceeds ${this.maxLineBytes} bytes`);
      this.onLine(line.toString("utf8"), line.length);
    }
  }

  reset(): void {
    this.buffered = Buffer.alloc(0);
  }
}

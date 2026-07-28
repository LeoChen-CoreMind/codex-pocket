interface Waiter {
  resolve: (release: () => void) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

export class AsyncSemaphore {
  private active = 0;
  private readonly waiters: Waiter[] = [];

  constructor(
    private readonly capacity: number,
    private readonly maxWaiters: number,
    private readonly waitTimeoutMs: number
  ) {}

  async run<T>(operation: () => Promise<T>): Promise<T> {
    const release = await this.acquire();
    try {
      return await operation();
    } finally {
      release();
    }
  }

  private acquire(): Promise<() => void> {
    if (this.active < this.capacity) {
      this.active++;
      return Promise.resolve(this.createRelease());
    }
    if (this.waiters.length >= this.maxWaiters) {
      return Promise.reject(new Error("Server read queue is full"));
    }

    return new Promise((resolve, reject) => {
      const waiter: Waiter = {
        resolve,
        reject,
        timeout: setTimeout(() => {
          const index = this.waiters.indexOf(waiter);
          if (index >= 0) this.waiters.splice(index, 1);
          reject(new Error("Server read queue wait timed out"));
        }, this.waitTimeoutMs)
      };
      waiter.timeout.unref();
      this.waiters.push(waiter);
    });
  }

  private createRelease(): () => void {
    let released = false;
    return () => {
      if (released) return;
      released = true;
      const waiter = this.waiters.shift();
      if (waiter) {
        clearTimeout(waiter.timeout);
        waiter.resolve(this.createRelease());
      } else {
        this.active--;
      }
    };
  }
}

import { EventEmitter } from "node:events";
import type WebSocket from "ws";
import type { BridgeEvent } from "../types.js";

interface Subscriber {
  socket: WebSocket;
  alive: boolean;
}

export class EventBus extends EventEmitter {
  private sequence = 0;
  private readonly events: BridgeEvent[] = [];
  private readonly subscribers = new Set<Subscriber>();
  private readonly heartbeat: NodeJS.Timeout;

  constructor(
    private readonly capacity: number,
    private readonly maxBufferedBytes: number
  ) {
    super();
    this.heartbeat = setInterval(() => this.pingSubscribers(), 30_000);
    this.heartbeat.unref();
  }

  publish<T>(type: string, threadId: string | null, payload: T): BridgeEvent<T> {
    const event: BridgeEvent<T> = {
      sequence: ++this.sequence,
      type,
      threadId,
      timestamp: Date.now(),
      payload
    };
    this.events.push(event);
    if (this.events.length > this.capacity) this.events.splice(0, this.events.length - this.capacity);

    const serialized = JSON.stringify(event);
    for (const subscriber of this.subscribers) {
      const socket = subscriber.socket;
      if (socket.readyState !== 1) {
        this.remove(subscriber);
        continue;
      }
      if (socket.bufferedAmount > this.maxBufferedBytes) {
        socket.terminate();
        this.remove(subscriber);
        continue;
      }
      socket.send(serialized, (error) => {
        if (error) this.remove(subscriber);
      });
    }
    this.emit("event", event);
    return event;
  }

  subscribe(socket: WebSocket, since: number | null): void {
    const subscriber: Subscriber = { socket, alive: true };
    this.subscribers.add(subscriber);
    socket.on("pong", () => {
      subscriber.alive = true;
    });
    socket.on("close", () => this.remove(subscriber));
    socket.on("error", () => this.remove(subscriber));

    if (since !== null) {
      const oldest = this.events[0]?.sequence ?? this.sequence;
      if (since < oldest - 1) {
        socket.send(JSON.stringify({
          sequence: this.sequence,
          type: "resync.required",
          threadId: null,
          timestamp: Date.now(),
          payload: { oldestAvailableSequence: oldest }
        }));
      } else {
        for (const event of this.events) if (event.sequence > since) socket.send(JSON.stringify(event));
      }
    }

    socket.send(JSON.stringify({
      sequence: this.sequence,
      type: "connection.ready",
      threadId: null,
      timestamp: Date.now(),
      payload: { currentSequence: this.sequence }
    }));
  }

  get currentSequence(): number {
    return this.sequence;
  }

  async poll(since: number, waitMs: number): Promise<{ data: BridgeEvent[]; currentSequence: number; resync: boolean }> {
    if (since > this.sequence) {
      return { data: [...this.events], currentSequence: this.sequence, resync: true };
    }
    const collect = () => this.events.filter((event) => event.sequence > since);
    let data = collect();
    if (data.length === 0 && waitMs > 0) {
      await new Promise<void>((resolvePromise) => {
        let settled = false;
        const finish = () => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          this.off("event", finish);
          resolvePromise();
        };
        const timer = setTimeout(finish, Math.min(waitMs, 25_000));
        this.once("event", finish);
      });
      data = collect();
    }
    const oldest = this.events[0]?.sequence ?? this.sequence;
    return { data, currentSequence: this.sequence, resync: since < oldest - 1 };
  }

  private pingSubscribers(): void {
    for (const subscriber of this.subscribers) {
      if (!subscriber.alive) {
        subscriber.socket.terminate();
        this.remove(subscriber);
        continue;
      }
      subscriber.alive = false;
      subscriber.socket.ping();
    }
  }

  private remove(subscriber: Subscriber): void {
    this.subscribers.delete(subscriber);
  }

  close(): void {
    clearInterval(this.heartbeat);
    for (const subscriber of this.subscribers) subscriber.socket.close(1001, "Server shutting down");
    this.subscribers.clear();
  }
}

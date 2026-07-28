import { parentPort } from "node:worker_threads";

if (!parentPort) throw new Error("JSON parser worker requires parentPort");

parentPort.on("message", (message: { id: number; text: string }) => {
  try {
    parentPort!.postMessage({ id: message.id, value: JSON.parse(message.text) });
  } catch (error) {
    parentPort!.postMessage({
      id: message.id,
      error: error instanceof Error ? error.message : String(error)
    });
  }
});

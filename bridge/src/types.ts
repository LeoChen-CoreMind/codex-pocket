export type JsonRpcId = string | number;

export interface JsonRpcRequest {
  id: JsonRpcId;
  method: string;
  params?: unknown;
}

export interface JsonRpcResponse {
  id: JsonRpcId;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

export interface JsonRpcNotification {
  method: string;
  params?: unknown;
}

export type JsonRpcMessage = JsonRpcRequest | JsonRpcResponse | JsonRpcNotification;

export type BridgeState = "starting" | "ready" | "restarting" | "stopped" | "failed";
export type CollaborationModeName = "default" | "plan" | "agent";

export interface BridgeFileReference {
  fileId: string;
  filename: string;
  filepath: string;
  type: string;
  bytes: number;
  width: number | null;
  height: number | null;
}

export interface ThreadSummary {
  id: string;
  title: string;
  projectName: string;
  cwd: string;
  source: string;
  updatedAt: number;
  archived: boolean;
  status: string;
}

export interface ChatRecord {
  threadId: string;
  turnId: string;
  itemId: string;
  role: "user" | "assistant";
  phase: "commentary" | "final" | null;
  text: string;
  state: "streaming" | "completed" | "interrupted";
  timestamp: number | null;
  kind?: "message" | "thinking" | "tool";
  tool?: string | null;
  imagePaths?: string[];
}

export interface BridgeEvent<T = unknown> {
  sequence: number;
  type: string;
  threadId: string | null;
  timestamp: number;
  payload: T;
}

export interface PendingInteraction {
  requestId: JsonRpcId;
  method: string;
  threadId: string | null;
  turnId: string | null;
  itemId: string | null;
  params: unknown;
  createdAt: number;
}

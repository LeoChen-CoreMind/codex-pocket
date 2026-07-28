const OPERATION_FIELDS = [
  "command",
  "aggregatedOutput",
  "output",
  "changes",
  "result",
  "summary",
  "text",
  "content",
  "message"
] as const;

const DATA_URL = /data:image\/[a-z0-9.+-]+;base64,[a-z0-9+/=\r\n]+/gi;

export function operationText(value: unknown): string {
  return [...new Set(collectText(value))].join("\n\n").trim();
}

export function operationItemText(item: Record<string, unknown>): string {
  if (item.type === "fileChange" && Array.isArray(item.changes)) {
    return item.changes.map((change) => formatFileChange(change)).filter(Boolean).join("\n\n");
  }
  return operationText(OPERATION_FIELDS.map((field) => item[field]));
}

function formatFileChange(value: unknown): string {
  if (!value || typeof value !== "object") return "";
  const change = value as Record<string, unknown>;
  const path = typeof change.path === "string" ? change.path : "文件";
  const kind = typeof change.kind === "string" ? change.kind : "update";
  const diff = typeof change.diff === "string" ? change.diff.trim() : "";
  const header = `${kind === "add" ? "新增" : kind === "delete" ? "删除" : "编辑"} ${path}`;
  return diff ? `${header}\n\n\`\`\`diff\n${diff}\n\`\`\`` : header;
}

export function liveOperationText(text: string, maxChars = 32_000): string {
  if (text.length <= maxChars) return text;
  return `${text.slice(0, maxChars)}\n\n[Live output shortened; reopen the thread to load the complete segmented history.]`;
}

function collectText(value: unknown): string[] {
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return [];
    if (trimmed.startsWith("data:image/")) return ["[Image data omitted]"];
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        return collectText(JSON.parse(trimmed) as unknown);
      } catch {
        // Plain command output can begin with JSON-like characters.
      }
    }
    return [trimmed.replace(DATA_URL, "[Image data omitted]")];
  }
  if (Array.isArray(value)) return value.flatMap(collectText);
  if (!value || typeof value !== "object") return [];

  const object = value as Record<string, unknown>;
  const type = typeof object.type === "string" ? object.type.toLowerCase() : "";
  if (type.includes("image") && !object.text) return ["[Image output omitted]"];
  return OPERATION_FIELDS.flatMap((field) => collectText(object[field]));
}

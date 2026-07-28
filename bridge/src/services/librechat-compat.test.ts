import assert from "node:assert/strict";
import test from "node:test";

import { compatResumeFrame } from "./librechat-compat.js";
import { operationItemText } from "./operation-text.js";

test("resume frame replaces previously streamed content", () => {
  const content = [
    { type: "think", think: "already received" },
    { type: "tool_call", tool_call: { id: "tool-1", name: "fileChange", output: "diff" } }
  ];

  assert.deepEqual(compatResumeFrame(content), {
    sync: true,
    resumeState: { aggregatedContent: content },
    pendingEvents: []
  });
});

test("file change operation text includes path and diff", () => {
  const text = operationItemText({
    type: "fileChange",
    changes: [{ path: "src/index.ts", kind: "update", diff: "-old\n+new" }]
  });

  assert.match(text, /编辑 src\/index\.ts/);
  assert.match(text, /```diff\n-old\n\+new\n```/);
});

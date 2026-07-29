import assert from "node:assert/strict";
import test from "node:test";

import { shouldRetry, type RetryPolicy } from "./thread-service.js";

const finitePolicy: RetryPolicy = {
  enabled: true,
  maxRetries: 3,
  untilSuccess: false,
  delaySeconds: 5,
  retryPrompt: "continue"
};

test("finite retry policy stops at the configured limit", () => {
  assert.equal(shouldRetry(finitePolicy, 0, false), true);
  assert.equal(shouldRetry(finitePolicy, 2, false), true);
  assert.equal(shouldRetry(finitePolicy, 3, false), false);
});

test("until-success policy ignores the finite limit", () => {
  assert.equal(shouldRetry({ ...finitePolicy, untilSuccess: true }, 100, false), true);
});

test("disabled or explicitly stopped retry chains never continue", () => {
  assert.equal(shouldRetry({ ...finitePolicy, enabled: false }, 0, false), false);
  assert.equal(shouldRetry(finitePolicy, 0, true), false);
});

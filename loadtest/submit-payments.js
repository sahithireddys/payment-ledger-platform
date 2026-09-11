// Load test for POST /api/v1/payments.
//
// Run: k6 run loadtest/submit-payments.js
// Override the target rate / duration / host with env vars, e.g.:
//   k6 run -e RATE=80 -e DURATION=3m loadtest/submit-payments.js
//
// Prerequisites: `docker compose up -d`, the app running, and
// loadtest/seed-accounts.sh already run against it (see loadtest/README.md).

import http from "k6/http";
import { check } from "k6";
import { SharedArray } from "k6/data";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const RATE = parseInt(__ENV.RATE || "60", 10); // requests/sec sustained
const DURATION = __ENV.DURATION || "2m";

const accounts = new SharedArray("accounts", function () {
  return JSON.parse(open("./accounts.json"));
});

export const options = {
  scenarios: {
    submit_payments: {
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: Math.max(50, RATE * 2),
      maxVUs: RATE * 4,
    },
  },
  thresholds: {
    // Mirrors the resume claim this load test exists to validate:
    // p95 submission latency below 250ms.
    http_req_duration: ["p(95)<250"],
    http_req_failed: ["rate<0.01"],
  },
};

function randomPair() {
  const i = Math.floor(Math.random() * accounts.length);
  let j = Math.floor(Math.random() * accounts.length);
  while (j === i) {
    j = Math.floor(Math.random() * accounts.length);
  }
  return [accounts[i], accounts[j]];
}

export default function () {
  const [from, to] = randomPair();
  const payload = JSON.stringify({
    fromAccountId: from,
    toAccountId: to,
    amount: Math.round((Math.random() * 100 + 1) * 100) / 100,
    currency: "USD",
  });

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, {
    "status is 202": (r) => r.status === 202,
  });
}

export function handleSummary(data) {
  const reqs = data.metrics.http_reqs.values.count;
  const durationSeconds = data.state.testRunDurationMs / 1000;
  const perMinute = (reqs / durationSeconds) * 60;
  const p95 = data.metrics.http_req_duration.values["p(95)"];

  console.log("");
  console.log("=== Payment submission load test summary ===");
  console.log(`Total requests:        ${reqs}`);
  console.log(`Test duration:         ${durationSeconds.toFixed(1)}s`);
  console.log(`Throughput:            ${perMinute.toFixed(0)} transactions/min`);
  console.log(`p95 submission latency: ${p95.toFixed(1)} ms`);
  console.log(`Failed requests:       ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log("==============================================");
  console.log("");

  return {
    stdout: "", // suppress k6's default text summary; we printed our own above
    "loadtest/results/summary.json": JSON.stringify(data, null, 2),
  };
}

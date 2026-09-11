# Load testing

Requires [k6](https://k6.io/) (`brew install k6`).

```bash
# 1. Start the infrastructure and the app (see the root README).
docker compose up -d
mvn spring-boot:run

# 2. Seed a pool of accounts to pay between (writes loadtest/accounts.json).
./loadtest/seed-accounts.sh 50

# 3. Run the load test.
k6 run loadtest/submit-payments.js

# Optional: push a higher sustained rate or run longer.
k6 run -e RATE=100 -e DURATION=3m loadtest/submit-payments.js
```

k6 prints a summary like:

```
=== Payment submission load test summary ===
Total requests:        7200
Test duration:         120.0s
Throughput:            3600 transactions/min
p95 submission latency: 180.3 ms
Failed requests:       0.00%
==============================================
```

`RATE` is requests/second sustained via k6's `constant-arrival-rate`
executor, so "throughput" is a direct, reproducible measurement rather than
whatever the default VU count happens to produce. The `thresholds` block in
`submit-payments.js` fails the run if p95 latency exceeds 250ms or more than
1% of requests error — that's what turns this from "a script that runs" into
a regression check you can wire into CI later.

Copy the real numbers this produces into the root README's benchmark
section (and, honestly, into your resume bullet) once you've run it —
don't reuse the placeholder numbers.

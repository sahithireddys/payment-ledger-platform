#!/usr/bin/env bash
# Creates a pool of accounts to run the load test against and writes their
# ids to loadtest/accounts.json (gitignored — regenerate whenever you
# restart the stack with a fresh database).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COUNT="${1:-50}"
OUT_FILE="$(dirname "$0")/accounts.json"

echo "Seeding $COUNT accounts against $BASE_URL ..."
echo "[" > "$OUT_FILE"

for i in $(seq 1 "$COUNT"); do
  RESPONSE=$(curl -sS -X POST "$BASE_URL/api/v1/accounts" \
    -H "Content-Type: application/json" \
    -d "{\"ownerName\":\"Load Test $i\",\"currency\":\"USD\",\"initialBalance\":1000000.00}")

  ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
  if [ "$i" -lt "$COUNT" ]; then
    echo "  \"$ID\"," >> "$OUT_FILE"
  else
    echo "  \"$ID\"" >> "$OUT_FILE"
  fi
done

echo "]" >> "$OUT_FILE"
echo "Wrote $COUNT account ids to $OUT_FILE"

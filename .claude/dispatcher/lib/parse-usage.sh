#!/usr/bin/env bash
# Parse a worker's claude -p stream-json log into a single per-task cost
# summary line (JSON), suitable for Promtail/Loki ingestion.
#
# Usage:
#   .claude/dispatcher/lib/parse-usage.sh <jsonl-path> <task-id> <final-state> [<duration-sec>]
#
# Output (stdout, single JSON line):
#   {
#     "ts": "2026-05-11T...",
#     "task": "CHORE-...",
#     "final_state": "MERGED",
#     "duration_sec": 412,
#     "input_tokens": <int>,
#     "cache_creation_tokens": <int>,
#     "cache_read_tokens": <int>,
#     "output_tokens": <int>,
#     "estimated_cost_usd": <float>,
#     "request_count": <int>
#   }
#
# Cost estimation uses Claude Opus 4.7 list pricing (rough, not authoritative):
#   - input          $15 / M tokens
#   - cache creation $18.75 / M tokens (1.25 × input)
#   - cache read     $1.50 / M tokens (0.10 × input)
#   - output         $75 / M tokens
# Override via env: PRICE_INPUT, PRICE_CACHE_CREATION, PRICE_CACHE_READ, PRICE_OUTPUT (per token).

set -uo pipefail

JSONL="${1:-}"
TASK="${2:-unknown}"
FINAL_STATE="${3:-unknown}"
DURATION_SEC="${4:-0}"

[[ -n "$JSONL" && -f "$JSONL" ]] || { echo "Usage: $0 <jsonl> <task-id> <final-state> [<duration-sec>]" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "jq required" >&2; exit 2; }

PRICE_INPUT="${PRICE_INPUT:-0.000015}"
PRICE_CACHE_CREATION="${PRICE_CACHE_CREATION:-0.00001875}"
PRICE_CACHE_READ="${PRICE_CACHE_READ:-0.0000015}"
PRICE_OUTPUT="${PRICE_OUTPUT:-0.000075}"

# Sum usage fields across every assistant `message_delta` event with a usage
# block. Use awk on the jq stream — faster than letting jq do the math for
# multi-MB logs. Each usage object's totals already accumulate within a
# single Claude turn; multiple turns concatenate.
# The log file mixes claude CLI stream-json output with worker.sh's own
# stderr lines (status messages, log_event records). Pre-filter to JSON
# lines only, then ask jq for the usage block from anywhere in the object
# tree (`..` recursive descent — handles both .event.usage from
# stream_event message_delta and .message.usage from assistant events).
read_tokens="$(grep -E '^[[:space:]]*\{' "$JSONL" 2>/dev/null \
  | jq -r '
      [.. | objects | select(has("usage")) | .usage]
      | map(select(. != null))
      | (.[]?
         | [(.input_tokens // 0),
            (.cache_creation_input_tokens // 0),
            (.cache_read_input_tokens // 0),
            (.output_tokens // 0)]
         | @tsv)
    ' 2>/dev/null \
  | awk '
      BEGIN { i=0; cc=0; cr=0; o=0; n=0 }
      NF>=4 { i+=$1; cc+=$2; cr+=$3; o+=$4; n++ }
      END { printf "%d\t%d\t%d\t%d\t%d\n", i, cc, cr, o, n }
    ')"

INPUT_TOKENS="$(printf '%s' "$read_tokens" | cut -f1)"
CACHE_CREATION="$(printf '%s' "$read_tokens" | cut -f2)"
CACHE_READ="$(printf '%s' "$read_tokens" | cut -f3)"
OUTPUT_TOKENS="$(printf '%s' "$read_tokens" | cut -f4)"
REQUEST_COUNT="$(printf '%s' "$read_tokens" | cut -f5)"

COST_USD="$(awk -v i="$INPUT_TOKENS" -v cc="$CACHE_CREATION" -v cr="$CACHE_READ" -v o="$OUTPUT_TOKENS" \
                -v pi="$PRICE_INPUT" -v pcc="$PRICE_CACHE_CREATION" -v pcr="$PRICE_CACHE_READ" -v po="$PRICE_OUTPUT" \
                'BEGIN { printf "%.6f", i*pi + cc*pcc + cr*pcr + o*po }')"

TS="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)"
HOST="${HOSTNAME:-$(hostname)}"

jq -nc \
  --arg ts "$TS" --arg host "$HOST" --arg task "$TASK" --arg state "$FINAL_STATE" \
  --argjson duration "${DURATION_SEC:-0}" \
  --argjson it "${INPUT_TOKENS:-0}" \
  --argjson cct "${CACHE_CREATION:-0}" \
  --argjson crt "${CACHE_READ:-0}" \
  --argjson ot "${OUTPUT_TOKENS:-0}" \
  --argjson reqs "${REQUEST_COUNT:-0}" \
  --argjson cost "${COST_USD:-0}" '
  {
    ts: $ts, host: $host, task: $task, final_state: $state,
    duration_sec: $duration,
    input_tokens: $it,
    cache_creation_tokens: $cct,
    cache_read_tokens: $crt,
    output_tokens: $ot,
    request_count: $reqs,
    estimated_cost_usd: $cost,
    component: "cost"
  }
'

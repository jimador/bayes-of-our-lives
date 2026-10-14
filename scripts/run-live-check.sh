#!/usr/bin/env bash
set -euo pipefail

# Up to three outings; stop at the first failed result. Tool turns may add HTTP calls.
outing_date=${1:-2026-10-20}
port=${BAYES_LIVE_PORT:-18085}
if (( $# > 1 )); then
  printf 'Usage: %s [destination-local YYYY-MM-DD]\n' "$0" >&2
  exit 2
fi
: "${OPENAI_API_KEY:?Provide the existing runtime OPENAI_API_KEY}"
: "${TYPESAFE_API_KEY:?Provide the existing runtime TYPESAFE_API_KEY}"
for tool in java mvn curl python3; do
  command -v "$tool" >/dev/null || { printf 'Required tool missing: %s\n' "$tool" >&2; exit 2; }
done
python3 - "$outing_date" "$port" <<'PY'
import datetime, re, socket, sys
from zoneinfo import ZoneInfo
value, port = sys.argv[1:]
if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', value):
    raise SystemExit('Choose a date in YYYY-MM-DD format.')
try:
    day = datetime.date.fromisoformat(value)
except ValueError:
    raise SystemExit('Choose a valid calendar date.')
now = datetime.datetime.now(ZoneInfo('America/New_York'))
if day < now.date() or (day == now.date() and now.time() >= datetime.time(14)):
    raise SystemExit('Choose today with a future finish, or a future date in Fort Lauderdale.')
if not port.isdigit() or not 1024 <= int(port) <= 65535:
    raise SystemExit('BAYES_LIVE_PORT must be an unused port from 1024 to 65535.')
try:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', int(port)))
except OSError:
    raise SystemExit('The chosen port is occupied; choose another BAYES_LIVE_PORT.')
PY
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd -- "$script_dir/.."
run_dir=$(mktemp -d "${TMPDIR:-/tmp}/bayes-live-check.XXXXXX")
run_stamp=$(date -u +'%Y%m%dT%H%M%SZ')
owned_pid=
stop_owned() {
  if [[ -n "$owned_pid" ]]; then
    kill "$owned_pid" 2>/dev/null || true
    wait "$owned_pid" 2>/dev/null || true
  fi
}
trap stop_owned EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'Synthetic date: %s; fresh outputs: %s\n' "$outing_date" "$run_dir"
mvn test package > "$run_dir/build.log" 2>&1
java -jar target/bayes-0.1.0.jar --server.port="$port" \
  --bayes.models.researcher=gpt-4.1 --bayes.models.planner=gpt-4.1 \
  --logging.level.root=ERROR --logging.level.dev.jimador.bayes=INFO \
  --embabel.agent.platform.models.openai.max-attempts=1 \
  --embabel.agent.platform.llm-operations.data-binding.max-attempts=1 \
  --embabel.agent.platform.decisions.llm.max-attempts=1 \
  > "$run_dir/server.log" 2>&1 &
owned_pid=$!
ready=false
for (( attempt=0; attempt<30; attempt++ )); do
  kill -0 "$owned_pid" 2>/dev/null || { printf 'Owned app stopped; inspect %s/server.log\n' "$run_dir" >&2; exit 1; }
  if curl --silent --fail --max-time 1 "http://127.0.0.1:$port/" >/dev/null; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || { printf 'Owned app did not become ready; inspect %s/server.log\n' "$run_dir" >&2; exit 1; }
failed=0
attempted=0
for engine in local jev openai; do
  kill -0 "$owned_pid" 2>/dev/null || exit 1
  attempted=$((attempted + 1))
  output="$run_dir/$run_stamp-$engine.json"
  started=$(python3 -c 'import time; print(time.monotonic_ns())')
  if http=$(curl --silent --show-error --max-time 300 \
    -o "$output" -w '%{http_code}' "http://127.0.0.1:$port/api/itinerary" \
    -H 'Content-Type: application/json' --data-binary @- <<JSON
{"city":"Fort Lauderdale, Florida","startingLocation":"Downtown Fort Lauderdale","date":"$outing_date","startTime":"07:00","endTime":"14:00","breakfast":true,"coffee":false,"earlyFoodDeclined":false,"lunch":true,"dinner":false,"partySize":2,"childrenCount":0,"interests":"art","pace":"steady","stepFreeAccess":false,"cuisineLikes":"seafood, cuban","cuisineDislikes":"italian","dietaryOrAllergyNeeds":"","engine":"$engine","publicMapConsent":true,"remoteModelConsent":true}
JSON
  ); then
    printf '%s HTTP %s; actual body: %s\n' "$engine" "$http" "$output"
  else
    printf '%s outing request failed; no resubmission; partial body: %s\n' "$engine" "$output" >&2
    failed=1
    break
  fi
  if ! python3 - "$output" "$http" "$engine" "$started" <<'PY'
import json, sys, time
path, http, engine, started = sys.argv[1:]
with open(path) as raw:
    data = json.load(raw)
decision = data.get('decision') or {}
selected = next((a for a in data.get('alternatives', []) if a.get('id') == data.get('selectedAlternative')), None)
print(json.dumps({'engine': engine, 'http': http, 'wallMs': round((time.monotonic_ns()-int(started))/1e6), 'backendMs': data.get('processMs'), 'status': data.get('status'), 'model': decision.get('model'), 'coverage': decision.get('coverage'), 'choice': decision.get('choice'), 'rating': decision.get('rating'), 'classification': decision.get('classification')}))
if http != '200' or data.get('status') != 'usable' or not selected or selected.get('status') != 'usable':
    raise SystemExit(1)
PY
  then
    failed=1
    break
  fi
done
python3 - "$run_dir/server.log" <<'PY'
import collections, re, sys
with open(sys.argv[1]) as log:
    lines = [line.strip() for line in log if 'stage=' in line]
print('Actual stage counts: ' + str(dict(collections.Counter(re.search(r'stage=(\S+)', line).group(1) for line in lines))))
for line in lines:
    print(line)
PY
printf '%s outing submission(s); zero warmups or outing retries. Configured retry templates use one attempt; tool turns and compatibility fallback can add HTTP calls. Times mix provider, network, model and app work.\n' "$attempted"
exit "$failed"

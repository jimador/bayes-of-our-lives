#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Usage: run-live-check.sh [YYYY-MM-DD] [options]
One submission per selected engine; real provider calls. Flags override BAYES_LIVE_*.
  --city TEXT             --origin TEXT           --date YYYY-MM-DD
  --start HH:MM           --end HH:MM             --interests TEXT
  --pace relaxed|steady|busy
  --meals breakfast,coffee,lunch,dinner | none
  --cuisine-likes TEXT    --cuisine-dislikes TEXT  (comma/semicolon cuisines)
  --engines local,jev,openai
  --timezone IANA_ZONE    --port NUMBER
  --fail-fast            --continue-on-failure   --help
Environment suffixes: CITY ORIGIN DATE START END INTERESTS PACE MEALS
CUISINE_LIKES CUISINE_DISLIKES ENGINES TIMEZONE PORT FAIL_FAST (true/false).
Defaults: Fort Lauderdale, Florida; Downtown Fort Lauderdale; 2026-10-20;
07:00–14:00; art; steady; breakfast,lunch; seafood, cuban / italian;
local,jev,openai; America/New_York; 18085; continue-on-failure.
HELP
}
city=${BAYES_LIVE_CITY-'Fort Lauderdale, Florida'}
origin=${BAYES_LIVE_ORIGIN-'Downtown Fort Lauderdale'}
outing_date=${BAYES_LIVE_DATE-2026-10-20}
start=${BAYES_LIVE_START-07:00}
end=${BAYES_LIVE_END-14:00}
interests=${BAYES_LIVE_INTERESTS-art}
pace=${BAYES_LIVE_PACE-steady}
meals=${BAYES_LIVE_MEALS-breakfast,lunch}
cuisine_likes=${BAYES_LIVE_CUISINE_LIKES-'seafood, cuban'}
cuisine_dislikes=${BAYES_LIVE_CUISINE_DISLIKES-italian}
engines=${BAYES_LIVE_ENGINES-local,jev,openai}
timezone=${BAYES_LIVE_TIMEZONE-America/New_York}
port=${BAYES_LIVE_PORT-18085}
fail_fast=${BAYES_LIVE_FAIL_FAST-false}
seen=' '
positional=false
while (( $# )); do
  option=$1
  shift
  case "$option" in
    --help) usage; exit 0 ;;
    --fail-fast|--continue-on-failure) field=fail_fast ;;
    --city|--origin|--start|--end|--interests|--pace|--meals|--engines|--timezone|--port)
      field=${option#--} ;;
    --date) field=outing_date ;;
    --cuisine-likes) field=cuisine_likes ;;
    --cuisine-dislikes) field=cuisine_dislikes ;;
    --*) printf 'Unknown option: %s\n' "$option" >&2; exit 2 ;;
    *)
      if [[ "$positional" == true || "$seen" == *' outing_date '* ]]; then
        printf 'Use one positional date or --date, not both.\n' >&2; exit 2
      fi
      outing_date=$option; positional=true; continue ;;
  esac
  if [[ "$seen" == *" $field "* || ( "$field" == outing_date && "$positional" == true ) ]]; then
    printf 'Duplicate option: %s\n' "$option" >&2; exit 2
  fi
  seen+="$field "
  case "$option" in
    --fail-fast) fail_fast=true ;;
    --continue-on-failure) fail_fast=false ;;
    *)
      (( $# )) || { printf 'Missing value for %s\n' "$option" >&2; exit 2; }
      printf -v "$field" '%s' "$1"; shift ;;
  esac
done
for tool in java mvn curl python3; do
  command -v "$tool" >/dev/null || { printf 'Required tool missing: %s\n' "$tool" >&2; exit 2; }
done
if ! scenario=$(python3 - "$city" "$origin" "$outing_date" "$start" "$end" "$interests" "$pace" \
  "$meals" "$cuisine_likes" "$cuisine_dislikes" "$engines" "$timezone" "$port" "$fail_fast" <<'PY'
import datetime as dt, json, re, socket, sys
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
city, origin, date, start, end, interests, pace, meals, likes, dislikes, engines, zone, port, policy = sys.argv[1:]
def require(ok, message):
    if not ok:
        raise SystemExit(message)
def controls(value):
    return any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value)
def java_length(value):
    return len(value.encode('utf-16-le')) // 2
for name, value, limit in [('city', city, 100), ('origin', origin, 120), ('interests', interests, 100)]:
    require(value.strip() and java_length(value) <= limit and not controls(value), f'Invalid {name}: nonblank, at most {limit} characters, no controls.')
cuisines = set('american argentinian barbecue brazilian british caribbean chinese colombian cuban ethiopian filipino french german greek indian indonesian italian jamaican japanese korean lebanese malaysian mediterranean mexican moroccan nepali pakistani persian peruvian pizza portuguese seafood southern spanish sushi thai turkish vietnamese'.split()) | {'middle eastern'}
def cuisine(raw):
    require(java_length(raw) <= 120 and not controls(raw), 'Invalid cuisine length or controls.')
    values = list(dict.fromkeys(' '.join(p.strip().lower().replace('_', ' ').split()) for p in re.split('[,;]', raw) if p.strip()))
    require(len(values) <= 8 and all(v in cuisines for v in values), 'Choose at most eight supported cuisines.')
    return values
likes, dislikes = cuisine(likes), cuisine(dislikes)
require(not set(likes) & set(dislikes), 'Cuisine likes and dislikes overlap.')
def members(raw, allowed):
    values = raw.split(',')
    require(all(v in allowed for v in values) and len(values) == len(set(values)), 'Choose nonempty, unique meals/engines from the listed values.')
    return values
engines = members(engines, {'local', 'jev', 'openai'})
meals = [] if meals == 'none' else members(meals, {'breakfast', 'coffee', 'lunch', 'dinner'})
require(pace in {'relaxed', 'steady', 'busy'}, 'Choose relaxed, steady, or busy pace.')
require(policy in {'true', 'false'}, 'BAYES_LIVE_FAIL_FAST must be true or false.')
require(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', date), 'Choose YYYY-MM-DD.')
require(all(re.fullmatch(r'(?:[01][0-9]|2[0-3]):[0-5][0-9]', t) for t in (start, end)), 'Choose strict HH:MM times.')
try:
    day = dt.date.fromisoformat(date)
    tz = ZoneInfo(zone)
except (ValueError, ZoneInfoNotFoundError):
    raise SystemExit('Choose a real calendar date and IANA timezone.')
a, b = (dt.datetime.combine(day, dt.time.fromisoformat(t)) for t in (start, end))
require(dt.timedelta(0) < b-a <= dt.timedelta(hours=12), 'Choose an increasing window of at most exactly 12 hours.')
for meal, lo, hi in [('lunch', '11:00', '14:30'), ('dinner', '17:00', '21:00')]:
    require(meal not in meals or (end >= lo and start <= hi), f'{meal} falls outside this window.')
def boundary(value):
    candidates = {value.replace(tzinfo=tz, fold=f).astimezone(dt.timezone.utc) for f in (0, 1)
                  if value.replace(tzinfo=tz, fold=f).astimezone(dt.timezone.utc).astimezone(tz).replace(tzinfo=None) == value}
    require(len(candidates) == 1, 'Choose boundaries without nonexistent or ambiguous daylight-saving times.')
    return next(iter(candidates)).astimezone(tz)
a, b = boundary(a), boundary(b)
require(a.utcoffset() == b.utcoffset(), 'Choose a window without a daylight-saving offset transition.')
now = dt.datetime.now(tz)
require(day >= now.date() and b.astimezone(dt.timezone.utc) > now.astimezone(dt.timezone.utc), 'Choose today with a future finish, or a future date in the destination timezone.')
require(re.fullmatch('[0-9]+', port) and 1024 <= int(port) <= 65535, 'Choose an unused port from 1024 to 65535.')
try:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', int(port)))
except OSError:
    raise SystemExit('The chosen port is occupied.')
print(json.dumps(dict(city=city, startingLocation=origin, date=date, startTime=start, endTime=end,
    breakfast='breakfast' in meals, coffee='coffee' in meals, earlyFoodDeclined=not {'breakfast', 'coffee'} & set(meals),
    lunch='lunch' in meals, dinner='dinner' in meals, partySize=2, childrenCount=0, interests=interests, pace=pace,
    stepFreeAccess=False, cuisineLikes=', '.join(likes), cuisineDislikes=', '.join(dislikes), dietaryOrAllergyNeeds='',
    publicMapConsent=True, remoteModelConsent=True)))
PY
); then exit 2; fi
[[ -n ${OPENAI_API_KEY:-} && -n ${TYPESAFE_API_KEY:-} ]] || { printf 'Provide both existing runtime OPENAI_API_KEY and TYPESAFE_API_KEY.\n' >&2; exit 2; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd -- "$script_dir/.."
slug=$(python3 -c 'import hashlib,re,sys; print(re.sub("[^a-z0-9]+", "-", sys.argv[1].lower()).strip("-") or "city-"+hashlib.sha256(sys.argv[1].encode()).hexdigest()[:12])' "$city")
mkdir -p results
run_dir=$(mktemp -d "results/live-check-$slug-$outing_date.XXXXXX")
run_name=${run_dir##*/}
summary="$run_dir/$run_name.md"
printf '# Live check: %s\n\nDate: %s; timezone: %s.\n\n| Engine | Outcome | HTTP | Wall ms | Backend ms | Capture |\n| --- | --- | --- | ---: | ---: | --- |\n' "$slug" "$outing_date" "$timezone" > "$summary"
owned_pid=
attempted=0
pending_output=
# shellcheck disable=SC2329 # Invoked by the EXIT trap.
stop_owned() {
  status=$?
  if [[ -n "$pending_output" ]]; then
    printf '| %s | interrupted (partial) | - | - | - | [JSON/partial](%s) |\n' "$engine" "${pending_output##*/}" >> "$summary"
  fi
  printf '\n%s outing submission(s); zero warmups or outing retries. Exit: %s.\n' "$attempted" "$status" >> "$summary"
  printf '%s outing submission(s); Summary: %s\n' "$attempted" "$summary"
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
  if curl --silent --fail --max-time 1 "http://127.0.0.1:$port/" >/dev/null; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { printf 'Owned app did not become ready; inspect %s/server.log\n' "$run_dir" >&2; exit 1; }
failed=0
IFS=',' read -r -a selected_engines <<< "$engines"
for engine in "${selected_engines[@]}"; do
  attempted=$((attempted + 1))
  output="$run_dir/$run_name-$engine.json"
  : > "$output"
  pending_output=$output
  payload=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); d["engine"]=sys.argv[2]; print(json.dumps(d))' "$scenario" "$engine")
  started=$(python3 -c 'import time; print(time.monotonic_ns())')
  transport=0
  http=$(curl --silent --show-error --max-time 300 \
    -o "$output" -w '%{http_code}' "http://127.0.0.1:$port/api/itinerary" \
    -H 'Content-Type: application/json' --data-binary @- <<< "$payload") || transport=$?
  if ! python3 - "$output" "$http" "$engine" "$started" "$transport" "$summary" <<'PY'
import json, pathlib, sys, time
path, http, engine, started, transport, summary = sys.argv[1:]
wall = round((time.monotonic_ns()-int(started))/1e6)
data = {}
outcome = 'usable'
if transport != '0':
    outcome = 'transport failure (partial)'
elif http != '200':
    outcome = 'HTTP failure'
else:
    try:
        data = json.loads(pathlib.Path(path).read_text())
    except (ValueError, UnicodeError):
        outcome = 'malformed/empty JSON'
    if outcome == 'usable':
        if not isinstance(data, dict) or data.get('status') != 'usable':
            outcome = 'top-level unusable'
        else:
            alternatives = data.get('alternatives')
            selected = data.get('selectedAlternative')
            if not isinstance(selected, str) or not isinstance(alternatives, list) or not any(
                    isinstance(a, dict) and a.get('id') == selected and a.get('status') == 'usable' for a in alternatives):
                outcome = 'missing/invalid selected entry'
backend = data.get('processMs') if isinstance(data, dict) else None
# Only numeric timings enter Markdown; retain all untrusted response text in the raw capture.
backend = backend if isinstance(backend, (int, float)) else '-'
with open(summary, 'a') as report:
    report.write(f'| {engine} | {outcome} | {http if http.isdigit() else "-"} | {wall} | {backend} | [JSON/partial]({pathlib.Path(path).name}) |\n')
print(f'{engine}: {outcome}; HTTP {http}; wall {wall} ms; capture: {path}')
raise SystemExit(outcome != 'usable')
PY
  then
    failed=1
    pending_output=
    [[ "$fail_fast" == false ]] || break
  fi
  pending_output=
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

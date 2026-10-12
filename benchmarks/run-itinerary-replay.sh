#!/usr/bin/env bash
set -euo pipefail
engine="${1:-}"
output="${2:-/tmp/bayes-itinerary-${engine}.log}"
case "$engine" in local|jev|openai|clef) ;; *) echo 'Usage: benchmarks/run-itinerary-replay.sh local|jev|openai|clef [output]' >&2; exit 2 ;; esac
test -f target/bays-0.1.0.jar || { echo 'Run mvn test package first' >&2; exit 2; }
if [[ "$engine" == jev && -z "${TYPESAFE_API_KEY:-}" ]]; then echo 'TYPESAFE_API_KEY is required' >&2; exit 2; fi
if [[ "$engine" == openai && -z "${OPENAI_API_KEY:-}" ]]; then echo 'OPENAI_API_KEY is required' >&2; exit 2; fi
args=()
if [[ "$engine" == clef ]]; then
  if [[ -z "${BAYS_CLEF_BRIDGE:-}" || -z "${CLEF_MODEL_DIR:-}" || -z "${CLEF_CHECKER_PATH:-}" ]]; then
    echo 'BAYS_CLEF_BRIDGE, CLEF_MODEL_DIR and CLEF_CHECKER_PATH are required' >&2; exit 2
  fi
  args+=("--bays.clef-bridge-path=$BAYS_CLEF_BRIDGE")
fi
java -jar target/bays-0.1.0.jar --bays.cli=true --bays.web=true --bays.replay=true \
  "--bays.replay.engine=$engine" "--bays.replay.warmups=${BAYS_REPLAY_WARMUPS:-1}" \
  "--bays.replay.runs=${BAYS_REPLAY_RUNS:-2}" \
  --embabel.agent.platform.decisions.llm.max-attempts=1 ${args[@]+"${args[@]}"} > "$output" 2>&1
python3 benchmarks/verify_itinerary_replay.py benchmarks/itinerary-capture.json "$output" "${BAYS_REPLAY_RUNS:-2}" "${BAYS_REPLAY_WARMUPS:-1}"
rg '^itineraryReplay ' "$output"
echo "result-log=$output"

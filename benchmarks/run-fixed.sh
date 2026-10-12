#!/usr/bin/env bash
set -euo pipefail

engine="${1:-}"
output="${2:-/tmp/bays-${engine}.log}"
case "$engine" in
  local|jev|openai|clef) ;;
  *) echo "Usage: benchmarks/run-fixed.sh local|jev|openai|clef [output-log]" >&2; exit 2 ;;
esac
test -f target/bays-0.1.0.jar || { echo "Run mvn test package first" >&2; exit 2; }
if [[ "$engine" == jev && -z "${TYPESAFE_API_KEY:-}" ]]; then
  echo "TYPESAFE_API_KEY is required in the process environment" >&2; exit 2
fi
if [[ "$engine" == openai && -z "${OPENAI_API_KEY:-}" ]]; then
  echo "OPENAI_API_KEY is required in the process environment" >&2; exit 2
fi
java -jar target/bays-0.1.0.jar \
  --bays.cli=true \
  --bays.mode=fixed --bays.intent=sights --bays.profile=art-quiet --bays.available-minutes=90 \
  "--demo.engine=$engine" --embabel.agent.platform.decisions.llm.max-attempts=1 > "$output" 2>&1
python3 benchmarks/verify_capture.py benchmarks/fixed-art-quiet.json "$output"
echo "result-log=$output"

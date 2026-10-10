#!/usr/bin/env bash
set -euo pipefail
engine=${1:-local}
case "$engine" in
  local) ;;
  jev) : "${TYPESAFE_API_KEY:?Set TYPESAFE_API_KEY in the runtime environment}" ;;
  openai) : "${OPENAI_API_KEY:?Set OPENAI_API_KEY in the runtime environment}" ;;
  *) echo 'Engine must be local, jev, or openai' >&2; exit 2 ;;
esac
mvn -q -DskipTests package
mkdir -p results
java -jar target/decision-desk-0.1.0.jar --demo.engine="$engine" --demo.warmups=1 --demo.runs=1 \
  | awk '/^(engine|local|jev|openai),/ { print }' > "results/$engine.csv"
python3 scripts/summarize.py results/*.csv

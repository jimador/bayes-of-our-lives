#!/usr/bin/env python3
"""Verify a Bays run used the recorded synthetic candidate and Dice-memory inputs."""
import json
from pathlib import Path
import re
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: verify_capture.py capture.json run.log", file=sys.stderr)
        return 2
    capture = json.loads(Path(sys.argv[1]).read_text())
    log = Path(sys.argv[2]).read_text()
    expected = {case["place"]: case["inputSha256"] for case in capture["cases"]}
    observed = dict(re.findall(r"^place=(.*?) source=.*? inputSha256=([0-9a-f]{64}) ", log, re.M))
    if observed != expected or capture["memoryProvenance"] not in log:
        print("capture-match=false", file=sys.stderr)
        return 1
    print(f"capture-match=true cases={len(expected)} context={capture['profile']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

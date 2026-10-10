#!/usr/bin/env python3
"""Summarize bounded CSV runs. Standard library only; never reads credentials."""
import csv
import statistics
import sys

for path in sys.argv[1:]:
    with open(path, newline="", encoding="utf-8") as file:
        rows = list(csv.DictReader(file))
    measured = [row for row in rows if row["phase"] == "measured"]
    if not measured:
        raise SystemExit(f"No measured rows: {path}")
    median = lambda key: statistics.median(float(row[key]) for row in measured)
    overhead = statistics.median(float(row["process_ms"]) - float(row["decision_ms"]) for row in measured)
    print(f"{path}: measured={len(measured)} correct={sum(row['correct'] == 'true' for row in measured)}/{len(measured)} "
          f"valid={sum(row['valid'] == 'true' for row in measured)}/{len(measured)} "
          f"decision_median_ms={median('decision_ms'):.3f} process_median_ms={median('process_ms'):.3f} "
          f"residual_median_ms={overhead:.3f}")

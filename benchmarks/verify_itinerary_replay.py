#!/usr/bin/env python3
"""Check that measured replay rows share the committed decision input and typed shape."""
import json
import pathlib
import re
import sys

capture = json.loads(pathlib.Path(sys.argv[1]).read_text())
rows = [line for line in pathlib.Path(sys.argv[2]).read_text().splitlines()
        if line.startswith('itineraryReplay ')]
expected_runs = int(sys.argv[3])
expected_warmups = int(sys.argv[4])
assert len(rows) == expected_runs + expected_warmups, (len(rows), expected_runs, expected_warmups)
for line in rows:
    fields = dict(re.findall(r'(\w+)=([^ ]+)', line))
    assert fields['inputSha256'] == capture['decisionInputSha256'], 'decision input changed'
    assert fields['choice'] in {'a', 'b', 'research'}, 'invalid typed choice'
    assert fields['rating'] in {'strong', 'possible', 'weak'}, 'invalid typed rating'
    assert fields['classification'] in {'sight-and-meal', 'sight-only', 'unknown'}, 'invalid classification'
    assert float(fields['decisionMs']) >= 0 and float(fields['classificationMs']) >= 0
print(f'verified={len(rows)} measured={expected_runs} warmups={expected_warmups} hash={capture["decisionInputSha256"]}')

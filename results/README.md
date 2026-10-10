# Synthetic coastal planning run, 2026-10-10

Environment: macOS arm64, Temurin Java 21.0.12.1, Embabel Agent 1.5.4-SNAPSHOT from the 2026-10-09 cached snapshot. TypeSafe's `jev-latest` resolved to `jev-1.13.0`; OpenAI used `gpt-4.1-mini`. Every engine received the same ten synthetic weather, tide, separate current, and simulated road-traffic cases. One ten-case pass was warmup and one ten-case pass was measured: 20 calls per engine, 60 in this final recorded comparison. The earlier preference-only measurements were replaced. No call failed.

| Engine | Raw policy match | Valid card | Guarded candidate | Median decision call | Median process | Median residual |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Local coastal rules | 10/10 | 10/10 | 0 | 0.166 ms | 3.397 ms | 3.221 ms |
| Jev 1.13.0 | 10/10 | 10/10 | 0 | 173.252 ms | 178.513 ms | 5.158 ms |
| GPT-4.1 mini | 8/10 | 10/10 | 2 | 483.010 ms | 487.502 ms | 5.129 ms |

The two measured GPT-4.1 mini mismatches selected `candidate` for `lowtide` (water height 1.5 ft above the **fictional** 2 ft MLLW access band) and `trafficlate` (simulated arrival 15:15 UTC after a 15:00 UTC window start). Both appeared in warmup too. The guard changed both cards to `review`; the raw responses remain in [`openai.csv`](openai.csv). `valid` means a selected legal label and completed card, **not** agreement with the rule. Raw rows: [`local.csv`](local.csv), [`jev.csv`](jev.csv), [`openai.csv`](openai.csv). Recompute with `python3 scripts/summarize.py results/*.csv`.

The ten cases cover exact numeric boundaries, gust and storm signals, low predicted water height, stronger predicted current, stale forecast, missing current, late road arrival, and unknown traffic. They do **not** cover actual station relevance, spatial variation, water-level changes throughout the window, wave period or direction, local hazards, paddler skill, equipment, or changing forecasts. The threshold values are invented for a software example and are not recommended paddling limits.

These are application round-trip measurements, not isolated model speed. Remote calls include client, network, provider, and parsing time. The residual is an arithmetic difference within each process run, not a controlled overhead experiment. Ten deliberately constructed fixtures are a wiring and boundary check, not a model quality benchmark. No live feed or local Jev model was used.

# Kayaking preference demo run, 2026-10-10

Environment: macOS arm64, Temurin Java 21.0.12.1, Embabel Agent 1.5.4-SNAPSHOT from the 2026-10-09 cached snapshot. TypeSafe's `jev-latest` resolved to `jev-1.13.0`; the OpenAI service used `gpt-4.1-mini`. Each engine saw the same six synthetic, explicitly worded preference notes: two paddle-today, two plan-later, two rest-day. One six-note pass warmed the JVM/client path; one six-note pass was measured. Twelve decision calls per engine; 36 total. There were no recorded failures. All 18 measured agent processes completed with a selected preference, nonempty next steps, and the hand-labeled category.

| Engine | Measured correct / valid | Median decision call | Median process | Median residual |
| --- | ---: | ---: | ---: | ---: |
| Local preference rules | 6/6 · 6/6 | 0.064 ms | 1.879 ms | 1.814 ms |
| Jev 1.13.0 | 6/6 · 6/6 | 165.366 ms | 169.137 ms | 3.148 ms |
| GPT-4.1 mini | 6/6 · 6/6 | 794.429 ms | 798.740 ms | 4.187 ms |

Raw rows: [`local.csv`](local.csv), [`jev.csv`](jev.csv), [`openai.csv`](openai.csv). Recompute with `python3 scripts/summarize.py results/*.csv` from the repository root. The first warmup request for each engine was noticeably longer; warmup rows are retained rather than folded into the medians.

These are **application round-trip measurements**, not isolated model latency. Remote calls include client work, network, provider time, and response parsing. The residual is an arithmetic difference within each process run. Six easy notes are not an accuracy study, and the custom rules are intentionally written for those notes. The choice labels describe stated preferences, not safe paddling conditions. A selected `paddle` label must not be interpreted as physical go/no-go advice. No local Jev model was downloaded or evaluated.

# Illustrative run, 2026-10-10

Environment: macOS arm64, Temurin Java 21.0.12.1, Embabel Agent 1.5.4-SNAPSHOT from the 2026-10-09 cached snapshot. TypeSafe's `jev-latest` resolved to `jev-1.13.0`; the OpenAI service used `gpt-4.1-mini`. Each engine saw the same six synthetic, hand-labeled tickets. One six-ticket pass warmed the JVM/client path; one six-ticket pass was measured. Twelve decision calls per engine; 36 total. No call failed, and all 18 measured outputs completed with a selected crew, nonempty checklist, and the expected label.

| Engine | Measured correct / valid | Median decision call | Median process | Median residual |
| --- | ---: | ---: | ---: | ---: |
| Local keywords | 6/6 · 6/6 | 0.188 ms | 4.178 ms | 4.020 ms |
| Jev 1.13.0 | 6/6 · 6/6 | 162.610 ms | 166.040 ms | 3.470 ms |
| GPT-4.1 mini | 6/6 · 6/6 | 823.324 ms | 829.195 ms | 4.512 ms |

Raw rows: [`local.csv`](local.csv), [`jev.csv`](jev.csv), [`openai.csv`](openai.csv). Recompute with `python3 scripts/summarize.py results/*.csv` from the repository root.

These are **round-trip application measurements**, not isolated model latency. They mix model work with client, network, provider, and parsing time. The residual is only an arithmetic difference inside one process run. The run is too small, the tickets too easy, and the services too different to support a general speed or quality claim. It demonstrates that the same agent completes useful work with all three decision implementations and records what happened on this machine at this time. No local Jev model was downloaded or evaluated.

# Fixed-context itinerary decision replay — 2026-10-12

This is a small illustrative check of the actual Embabel Agent 1.5.4-SNAPSHOT `BayesAgent.decideItinerary` action. One synthetic Miami outing was captured after place discovery and proposal, then replayed unchanged through four decision implementations. Replay skips map calls, website retrieval, Dice capture, and the rest of agent orchestration. It tests interchangeable typed decisions on **the same** proposed outing; it is not an end-to-end latency benchmark.

## Capture and validity

The [capture](../benchmarks/itinerary-capture.json) represents an October 14, 2026, Miami outing with breakfast, art sight, and lunch for two adults, 07:00–14:00, steady pace. It contains two alternatives. The mapped `opening_hours` evidence makes A invalid; B is unresolved because current venue and admission details remain unknown. Page retrieval status is included but short website snippets and random Dice IDs were replaced with synthetic fixed values before committing the capture. The exact UTF-8 decision input's SHA-256 was `e835eaa08dd0c0c8eca1d5c3bc2ed71a368a1453fd8a600d8daaf7fa213db46d` on every warmup and measured run. The verifier checks the hash, typed outputs, run counts, and A/B hard-rule status.

A choice of B is a tentative selection, not a confirmed plan. `research` is also a valid decision on this incomplete evidence. There is no unique ground-truth winner for B versus research. Every measured response parsed into the expected decision and classification types, and no model choice turned invalid A into a presented plan. No transport, timeout, or typed-response failures occurred in these runs.

## Procedure and observations

One process per engine. Local rules, TypeSafe Jev, and OpenAI each had **one warmup and two measured runs**. CLEF had **zero warmups and one measured run** due the prepared local CPU model's cost. All runs were sequential, with no retries. Jev used existing runtime `TYPESAFE_API_KEY`; OpenAI used existing runtime `OPENAI_API_KEY`; no key values were saved. CLEF used a separately prepared, local single-function L1024 Core ML variant derived from `FluidInference/clef-text-0.6b-coreml`; its weights/checker are not in this repo.

| Engine | Measured typed outcome | Decision call, ms | Classification call, ms | Count |
| --- | --- | ---: | ---: | ---: |
| Local Java rules | `false`, B, possible; sight-and-meal | 0.264, 0.193 | 0.022, 0.019 | 2 |
| TypeSafe `jev-1.13.0` | `false`, research, possible; unknown | 194.449, 171.103 | 189.180, 150.374 | 2 |
| OpenAI `gpt-4.1` | `false`, research, weak; unknown | 1131.743, 981.781 | 544.171, 532.104 | 2 |
| Local CLEF derived L1024 | `false`, B, possible; sight-and-meal | 2268.264 | 879.474 | 1 |

The warmup decision/classification pairs were local 4.330/0.072 ms, Jev 410.186/197.411 ms, and OpenAI 2637.939/744.918 ms. CLEF's bridge reported **786** decision input tokens and **587** classification input tokens, within the prepared 1024-token limit. For its measured run, model inference was 1925.358/557.646 ms and bridge wall time 2223.603/847.568 ms for decision/classification respectively. Java call times include bridge launch and response parsing beyond those numbers. Remote call times include client adapter, network, provider processing, and parsing; provider inference time is not separately available. Local Java has no model/network component. None of these figures includes fresh place lookup or complete UI request time.

The sample is too small for a distribution, speed ranking, quality ranking, or model comparison claim. Network conditions and provider load were uncontrolled. CLEF used a locally derived limited-context variant, not an untouched publisher multifunction artifact. Its Python bridge is an experimental legacy adapter pending a separate provider redesign. Captured OSM facts can age; the fixed capture is for repeatability, not today's travel advice.

## Reproduce

Build with `mvn test package`; run `benchmarks/run-itinerary-replay.sh local|jev|openai|clef` with the required existing runtime credentials or CLEF paths. The runner defaults to one warmup and two measured runs; set `BAYS_REPLAY_WARMUPS=0 BAYS_REPLAY_RUNS=1` for the one-run CLEF procedure above. It prints a compact result line and verifies the hash and outcome. Full logs default to `/tmp` and can include model response detail; keep them out of Git. A future run may return a different valid typed choice or fail due remote service changes. The older [live outing check](live-itinerary-check-2026-10-12.md) made one full request per remote engine but had unequal live decision inputs; it is separately labeled as a smoke observation.

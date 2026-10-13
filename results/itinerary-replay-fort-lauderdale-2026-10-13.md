# Fixed-context itinerary decision replay, Fort Lauderdale, 2026-10-13

This is a small illustrative check of the Embabel Agent 1.5.4-SNAPSHOT `BayesAgent.decideItinerary` action. One live Fort Lauderdale outing was captured after research and proposal and replayed unchanged through three decision implementations. Replay skips discovery, page research, routing, planning, Dice capture and the rest of the agent run. It is not an end-to-end latency benchmark.

## Capture and validity

The [capture](itinerary-capture-fort-lauderdale-2026-10-13.json) is the local-rules request from the [live check](live-fort-lauderdale-check-2026-10-13.md): Fort Lauderdale, Tuesday 2026-10-20, 07:00 to 14:00, breakfast, art sight and lunch for two adults, steady pace. It holds the request, accepted memory (art, steady), the eight researched places and two alternatives. A is NSU Art Museum plus Restaurante Brasil, B is Bonnet House plus Restaurante Brasil. Both are `unresolved` with five open questions each (no sourced breakfast venue, hours on the date, admission, lunch cuisine). Neither is invalid. The capture was built from the live API response JSON without hand edits. Unlike the Miami capture, research snippets and Dice IDs were not replaced with synthetic values.

The exact UTF-8 decision input SHA-256 was `a7f195150064727cc381300665665175104b72041249e3dd1f53c4a56555adb6` on every warmup and measured run for every engine. It matches the decision input hash of the live local request, so the replay reached the same boundary with the same input.

Every response parsed into the expected decision and classification types. No transport, timeout or typed-response failures occurred. There is no ground-truth winner between a tentative A and `research` on this evidence.

## Procedure

The current tree no longer contains the replay runner (`ItineraryReplayRunner`, `benchmarks/run-itinerary-replay.sh`), so the replay used the runner from commit `da8b0f1~1` exported with `git archive` into `/tmp` and built with `mvn -DskipTests package`. Between that commit and `83e659d` the decision path (`BayesAgent`, `BayesDecisionServices`, `ItineraryPlanner`) only moved packages and dropped the CLEF engine. The command per engine was:

```sh
java -jar target/bays-0.1.0.jar --bays.cli=true --bays.web=true --bays.replay=true \
  --bays.replay.capture=/path/to/itinerary-capture-fort-lauderdale-2026-10-13.json \
  --bays.replay.engine=local|jev|openai --bays.replay.warmups=1 --bays.replay.runs=2 \
  --embabel.agent.platform.decisions.llm.max-attempts=1
```

One process per engine, sequential, one warmup and two measured runs, no retries, run at about 11:15 ET. Jev used existing runtime `TYPESAFE_API_KEY` and OpenAI used existing runtime `OPENAI_API_KEY`. The old `verify_itinerary_replay.py` was not used because it is tied to the Miami capture's A invalid / B unresolved shape. Hash and typed outputs were checked by hand from the result lines.

## Observations

| Engine | Measured typed outcome (completeness, choice, rating, classification) | Decision call, ms | Classification call, ms | Count |
| --- | --- | ---: | ---: | ---: |
| Local rules `local/bayes-itinerary-rules-v1` | `false`, a, possible, sight-and-meal | 0.371, 0.229 | 0.037, 0.052 | 2 |
| TypeSafe `jev-1.13.0` | `false`, research, possible, unknown | 144.410, 216.867 | 183.086, 159.798 | 2 |
| OpenAI `gpt-4.1` | run 1 `false`, run 2 `true`; research, possible, unknown | 772.377, 743.300 | 849.662, 630.182 | 2 |

Warmup decision/classification pairs were local 4.973/0.101 ms, Jev 359.606/149.128 ms and OpenAI 1091.997/728.011 ms (warmup completeness `false`).

OpenAI's second measured run answered `true` for `fully-sourced` while both alternatives had open questions, and still chose `research`. The other OpenAI calls and all Jev and local calls answered `false`.

Remote call times include client adapter, network, provider processing and parsing. Provider inference time is not separately available. Local rules have no model or network component. Two samples do not support a distribution, speed ranking or quality ranking. Network and provider load were uncontrolled. Captured OSM facts and venue pages will age.

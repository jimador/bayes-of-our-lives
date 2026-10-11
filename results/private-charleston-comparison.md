# Private Charleston comparison, 2026-10-10

**Historical fixture:** this measured the earlier Charleston-specific prompt, three county-named places, and creek/sound categories. The current city-input catalog uses fictional place labels and intracoastal/ocean categories. Do not present these rows as measurements of the current city-input app or compare them as a controlled before/after test. See `private-city-miami-validation.md` for one small current-path smoke run.

This is a private, exploratory measurement on synthetic fixtures. It is not a safety judgment about real water or a provider performance benchmark. Do not publish these results or the changed project until the parent thread receives approval.

## Exact setup

- Project: local uncommitted `private-charleston-planning` checkout; Embabel Agent `1.5.4-SNAPSHOT`, Dice `0.4.0`; Java app packaged with `mvn -o -q test package`.
- Provider versions reported by `ModelProvenance`: TypeSafe `jev-1.13.0` and OpenAI `gpt-4.1-mini`; no finer model version reported. The local reference reports `coastal-rules-v1`.
- One measured pass for each engine over two fixture sets (`calm`, `mixed`), three fictionalized ramp records each. No warmup. Each ramp makes one mixed `DecisionRequest` with a proposition, choice, and rating, followed by one separate `ClassificationRequest`. Thus six provider calls per fixture and twelve per engine. The local reference makes no network calls. `--embabel.agent.platform.decisions.llm.max-attempts=1` was set on remote runs.
- Jev was first run once on both fixtures with raw rating levels only. Those six rows are diagnostic, not in the combined CSV. The source shows its rating answer intentionally has no `selectedLevelId`, instead returning a distribution and expected-level-index score. The agent initially withheld all ramps. We then added a documented local projection: choose the highest-probability level only if its probability is strictly greater than 0.5. The six Jev rows in the CSV are the single measured pass after that change. OpenAI rows come from the earlier measured pass; its selected levels remain unchanged by the projection. The CSV fills `rating_projection` with those selected levels and `rating_evidence=not-recorded` for OpenAI because its distribution was not logged at that time.
- The single agent action path was `gather -> assess -> preferences -> rank -> finish` for every run. Eligibility requires the deterministic local policy to say `candidate`, the returned choice to say `candidate`, the proposition to be true, the projected rating to be `easy`, and a valid waterway class. Only then does the local `Ranker` rank the ramp. The goal card carries the selected ramp or an explicit no-candidate status.

## Observations

| Engine | Correct proposition / 6 | Correct choice / 6 | Correct projected rating / 6 | Correct classification / 6 | Eligible ramps across both fixtures | Final card, calm / mixed |
|---|---:|---:|---:|---:|---:|---|
| Local reference | 6 | 6 | 6 | 6 | 4 | Palmetto / Palmetto |
| Jev | 6 | 5 | 4 | 6 | 4 | Palmetto / Palmetto |
| OpenAI | 5 | 4 | 4 | 6 | 4 | Palmetto / Palmetto |

These counts measure agreement with the deliberately coded synthetic policy, not broad correctness or safety. In the mixed fixture, both remote engines called the traffic delayed Pierpoint ramp a candidate; OpenAI also called the missing-current Gadsdenville ramp a candidate. The deterministic guard excluded both. Jev's distribution also rated those two ramps `easy` by majority, despite their local policy labels. No provider call or agent process failed in the measured runs. The initial Jev diagnostic runs also completed; their ratings were `Answered` with no selected level, which is valid evidence under this API.

| Engine | Decision call median, ms, n=6 | Classification call median, ms, n=6 | Agent process, calm / mixed, ms | Local rank, calm / mixed, ms |
|---|---:|---:|---:|---:|
| Local reference | 0.283 | 0.026 | 45.683 / 48.400 | 0.114 / 0.115 |
| Jev | 243.514 | 177.087 | 1419.149 / 1360.209 | 0.123 / 0.219 |
| OpenAI | 880.518 | 778.447 | 6393.241 / 5603.983 | 0.197 / 0.127 |

`decision_ms` is wall time around each three-question decision call. `classification_ms` is wall time around the separate classification call. Each remote call includes client, network, service, model, and parsing time; **model inference time and network time were not separately measured**. `rank_ms` is local Java work only. `process_ms` surrounds `AgentProcess.run()` and excludes JVM/Spring startup but includes local action/framework overhead as well as the provider calls. There is one process time per fixture; it is repeated on its three ramp rows for join convenience and should not be summed. The remote/local differences cannot be attributed to model compute. One small pass, ordering, caches, and network conditions preclude performance claims.

## Reproduction

Use the existing runtime environment for `TYPESAFE_API_KEY` and `OPENAI_API_KEY`; do not put keys in files or command lines. No secrets were recorded in these result files. The fixtures are built into `DeskRunner` and synthetic. The printed CSV rows can be collected from each invocation. Exact commands:

```sh
mvn -o -q test package
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.mode=compare --demo.compare-fixture=calm
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.mode=compare --demo.compare-fixture=mixed
java -jar target/decision-desk-0.1.0.jar --demo.engine=jev --demo.mode=compare --demo.compare-fixture=calm --embabel.agent.platform.decisions.llm.max-attempts=1
java -jar target/decision-desk-0.1.0.jar --demo.engine=jev --demo.mode=compare --demo.compare-fixture=mixed --embabel.agent.platform.decisions.llm.max-attempts=1
java -jar target/decision-desk-0.1.0.jar --demo.engine=openai --demo.mode=compare --demo.compare-fixture=calm --embabel.agent.platform.decisions.llm.max-attempts=1
java -jar target/decision-desk-0.1.0.jar --demo.engine=openai --demo.mode=compare --demo.compare-fixture=mixed --embabel.agent.platform.decisions.llm.max-attempts=1
```

Each invocation starts a fresh JVM and Spring context. The local ranking policy, prompt, and fixtures are in `PaddleComparisonAgent.java` and `DeskRunner.java`. The CSV is `results/private-charleston-comparison.csv`. The source draft API inspection was against resolved `1.5.4-SNAPSHOT` source jars. A future snapshot can differ, so pin the resolved artifact timestamp for a publication grade rerun.

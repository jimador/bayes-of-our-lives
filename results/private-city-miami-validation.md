# Private city-input smoke comparison, 2026-10-10

These are synthetic Miami *scenario labels*, not real launches or conditions. The data in [private-city-miami-validation.csv](private-city-miami-validation.csv) is one run of the `mixed` fixture for each engine: three place/time candidates per run, one three-question `DecisionRequest` and one `ClassificationRequest` per candidate, no warmup, and no repeated trials. Both remote services had LLM max attempts set to 1. Jev provenance reported TypeSafe `jev-1.13.0`; OpenAI reported `gpt-4.1-mini`; finer model versions were unreported. All three processes completed and selected `Miami Demo Intracoastal A`, 16:00–18:00 UTC, after the local eligibility guard and local `Ranker`.

Agreement with the *coded synthetic policy* (proposition/choice/projected rating/classification, out of three candidates): local 3/3/3/3; Jev 3/3/1/3; OpenAI 2/1/1/3. Jev rated the delayed and missing-current scenarios `easy` by its reported distribution majority, although its choice and proposition handled those records correctly. OpenAI returned `candidate` for both and `true` for the missing-current proposition. The local guard excluded both from final ranking. These counts are a tiny fixture check, not accuracy or safety evidence. There were no typed failure outcomes in this run.

`AgentProcess.run()` wall times, excluding JVM/Spring startup: local 90.142 ms, Jev 1695.881 ms, OpenAI 6692.040 ms. Local `Ranker.rank()` times: 0.350, 0.314, 0.192 ms respectively. Per-call decision/classification timings are in the CSV. Remote wall times include client, network, service, model, and parsing; model and network time cannot be separated from this measurement. These isolated numbers are neither provider benchmarks nor evidence that Jev is generally faster. The previous Charleston CSV used a different prompt, catalog, and labels and is not comparable as a controlled before/after result.

Reproduce using the existing runtime `TYPESAFE_API_KEY` and `OPENAI_API_KEY`, without storing either key:

```sh
mvn -o -q test package
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.mode=compare --demo.city=Miami --demo.compare-fixture=mixed
java -jar target/decision-desk-0.1.0.jar --demo.engine=jev --demo.mode=compare --demo.city=Miami --demo.compare-fixture=mixed --embabel.agent.platform.decisions.llm.max-attempts=1
java -jar target/decision-desk-0.1.0.jar --demo.engine=openai --demo.mode=compare --demo.city=Miami --demo.compare-fixture=mixed --embabel.agent.platform.decisions.llm.max-attempts=1
```

# Live Fort Lauderdale itinerary check, 2026-10-13

This is a bounded functional check of the full web API (`POST /api/itinerary`), one request per decision engine. It is not a timing or model-quality benchmark. Public place, page and route sources can change between requests, and each request ran its own OpenAI research and planning, so the three engines saw **different** alternatives and different decision inputs. There were no warmup requests and no retries.

Build: working tree at `83e659d` (only `docs/drafts/blog.md` modified), `mvn test package` passed. Server: `java -jar target/bays-0.1.0.jar --server.port=8091`. Requests ran sequentially from 11:11 to 11:12 ET using existing runtime `OPENAI_API_KEY` and `TYPESAFE_API_KEY`. No key values were saved.

Request (same for every engine except `engine`): `Fort Lauderdale, Florida`, starting point `Downtown Fort Lauderdale`, Tuesday 2026-10-20, 07:00 to 14:00, breakfast and lunch, two adults, no children, interests `art`, pace `steady`, no step-free or dietary needs, public-map and remote-model consent on, no saved preferences.

## Results

| Engine | HTTP | Status | Final answer (completeness, choice, rating, classification) | Follow-up laps | Decision ms | Classification ms | Decision input SHA-256 | Process ms | curl total |
| --- | ---: | --- | --- | ---: | ---: | ---: | --- | ---: | ---: |
| Local rules `local/bayes-itinerary-rules-v1` | 200 | unresolved, A selected | `false`, a, possible, sight-and-meal | 0 | 4.496 | 0.045 | `a7f195150064727cc381300665665175104b72041249e3dd1f53c4a56555adb6` | 27,430 | 27.5 s |
| TypeSafe `jev-1.13.0` | 200 | needs_research | `false`, research, possible, unknown | 1 | 486.408 | 333.204 | `bc65a293ece0de8ef0dac77e793e3e56617b7cf2a1734a4f395d1203ee56bb81` | 31,626 | 31.6 s |
| OpenAI `gpt-4.1` | 200 | needs_research | `false`, research, possible, unknown | 1 | 1,882.590 | 1,375.602 | `e4768209e3c48ad5268204be1ed44b294db06721484c055b0974954a6f36220a` | 33,257 | 33.3 s |

For Jev and OpenAI the decision and classification times are the sum of the first decision and the one follow-up decision after the extra research lap. All three responses parsed into the expected typed answers. No request failed.

## What the agent found

All three runs researched the same eight places: NSU Art Museum Fort Lauderdale, Bonnet House Museum and Gardens, Cafe Siena, Cafe Sportivo, Cafe Cita, Restaurante Brasil, Mai-Kai Restaurant and Polynesian Show and Southport Raw Bar & Restaurant. Each run proposed two alternatives built around one of the two art sights. Mapped hours were `Tu-Sa 11:00-17:00; Su 12:00-17:00` for NSU Art Museum (5 min base drive) and `Tu-Su 11:00-16:00` for Bonnet House (9 min). Both are open on Tuesday, and no alternative was marked invalid in any run. Every alternative was `unresolved`.

- Local: A "Art Morning at NSU Museum & Brazilian Lunch" and B "Bonnet House Art & Gardens, Lunch Afterward". Both used Restaurante Brasil for lunch (21 and 26 min base drive) and had no sourced breakfast venue. Five open questions each. Rules took A tentatively.
- Jev: A "Modern Art Morning & Classic Island Estate" (Bonnet House) and B "Downtown Art Discovery" (NSU). No sourced breakfast or lunch venue. Four open questions each. Still `research` after one follow-up lap.
- OpenAI: A "Artful Morning at NSU Art Museum" and B "Bonnet House Gardens and Art Escape". No sourced breakfast or lunch venue. Four open questions each. Still `research` after one follow-up lap.

Open questions were missing breakfast (all runs) and lunch (Jev and OpenAI runs) venues, unverified hours on the chosen date, admission, and cuisine for Restaurante Brasil. Hours fit stayed `unknown` because the planner did not assign stop times. The breakfast gap is real for a 7 a.m. start since both sights open at 11:00.

The server log repeated an `AStarGoapPlanner` ERROR about unsatisfied conditions for the goal `BayesAgent.finish` during each request. The itinerary requests still completed.

The local run's alternatives, research and memory were saved as [itinerary-capture-fort-lauderdale-2026-10-13.json](itinerary-capture-fort-lauderdale-2026-10-13.json) and used for the [fixed replay](itinerary-replay-fort-lauderdale-2026-10-13.md).

# Live city outing validation, 2026-10-11 UTC

This is a bounded **live public-data functional check** of Tripper's new `CityOutingAgent`, not a trip-quality or provider-speed benchmark. Each run completed the same Embabel action path: `preferences → discover → assess → rank → finish`. The destination was `Miami, Florida`, origin the geocoded Miami city center, proposed hour **2026-10-11 18:00 UTC** (2 pm local), and an explicitly simulated 15-minute traffic delay. The goal card stayed `research` under all engines. No opening hours, admission, accessibility, or live traffic were verified.

The public provider made one Open-Meteo geocoding request, one user-triggered OSM Nominatim search (`museum, Miami, Florida`, at most three leads), three Open-Meteo hourly weather requests, and three OSRM base-driving requests per run. Nominatim's identifying User-Agent and OSM attribution are in the implementation/CLI. Place data © OpenStreetMap contributors, ODbL. The same mapped OSM entities and sampled forecast values appeared in the three runs, though each provider call was live and independently timed:

| Mapped museum lead | OSM source | Forecast temperature | Rain probability | Wind | OSRM base drive |
| --- | --- | ---: | ---: | ---: | ---: |
| Pérez Art Museum Miami | [OSM way 484587327](https://www.openstreetmap.org/way/484587327) | 30.4 °C | 1% | 7.7 kt | 4 min |
| Patricia & Phillip Frost Art Museum | [OSM way 112768020](https://www.openstreetmap.org/way/112768020) | 30.7 °C | 4% | 5.0 kt | 24 min |
| Patricia and Phillip Frost Museum of Science | [OSM way 319426548](https://www.openstreetmap.org/way/319426548) | 30.4 °C | 1% | 7.7 kt | 4 min |

The fictional local policy labeled all three `candidate` because required values were present, rain probability was at most 50%, and base drive plus simulated delay was at most 45 minutes. `candidate` means a research lead. Decision answers, separately from the local policy:

| Engine | Proposition true | Choice candidate | Rating easy | Classification nearby | Ranked leads | Typed choice/rating failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Local `tripper-poi-rules-v1` | 3/3 | 3/3 | 3/3 | 3/3 | 3 | 0 |
| TypeSafe `jev-1.13.0` | 3/3 | 3/3 | 3/3 | 3/3 | 3 | 0 |
| OpenAI `gpt-4.1-mini` | 3/3 | 1/3 | 1/3 | 3/3 | 1 | 2 `INVALID_RESPONSE` for choice and rating |

All three processes completed and selected **Pérez Art Museum Miami** as their first research lead. The OpenAI failures were typed outcomes; the eligibility gate excluded those two options, rather than treating them as affirmative choices. Classification succeeded on all nine option evaluations. No runtime key was printed or written.

## Timings and interpretation

| Engine | Public-provider action | Entire `AgentProcess.run()` | Local `Ranker.rank()` | Warmups | Measured runs |
| --- | ---: | ---: | ---: | ---: | ---: |
| Local | 4554.117 ms | 4965.509 ms | 0.579 ms | 0 | 1 |
| JEV | 4815.429 ms | 6549.250 ms | 0.584 ms | 0 | 1 |
| OpenAI | 5557.615 ms | 11488.994 ms | 0.532 ms | 0 | 1 |

Timing begins inside the agent process, after JVM/Spring startup. The public-provider action includes geocoding, OSM, weather, OSRM, client work, and network latency. Each option has one mixed decision call and a separate classification call; per-option client timings and source URLs are printed by the CLI. Remote decision call timings include network, queueing, model work, and parsing; this harness cannot isolate model time. Public-provider latency varied even for matching inputs. One un-warmed run per engine supports no general speed claim.

A keyless local run for `Lisbon, Portugal` also completed, found three mapped museums, and selected Museu Calouste Gulbenkian as the first research lead for **2026-10-11 13:00 UTC**, its next local 2 pm. This checks arbitrary geocodable destination input; it does not promise worldwide place or routing coverage. An optional Dice-enabled Miami run completed with `preferenceSource=dice-graph`, read the fictional `art` preference, and stopped its app-owned Neo4j container after exit. It used a different traffic-delay setting (zero), so it is not part of the three-engine comparison.

## Reproduce a current run

Use Java 21 and Maven. The CLI-only invocation below supplies inert OAuth placeholders because Tripper's existing configuration declares those properties; they do not enable web login. The public providers need no key. Run at most a few manual comparisons and respect the [public Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).

```sh
mvn test package
GOOGLE_CLIENT_ID=unused GOOGLE_CLIENT_SECRET=unused java -jar target/tripper-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --tripper.outing.run=true --tripper.decision.engine=local '--tripper.outing.destination=Miami, Florida' '--tripper.outing.origin=Miami, Florida' --tripper.outing.simulated-traffic-delay-minutes=15
```

Change only `--tripper.decision.engine=jev` or `openai` to compare on new live readings, using the existing process environment `TYPESAFE_API_KEY` or `OPENAI_API_KEY`. The default OpenAI model is `gpt-4.1-mini`; do not put keys in files or command arguments. Omit `--tripper.outing.origin` to use the destination city center. `--tripper.outing.start-utc=<future ISO instant>` fixes a UTC hour within six days; otherwise the next local 2 pm is chosen. `--tripper.dice.enabled=true` enables the owned disposable graph preference, requiring Docker. A live result cannot be exactly replayed after forecasts or mapped places change.

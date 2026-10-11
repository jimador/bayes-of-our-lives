# Embabel Decision Desk: three museums, three ways to decide

**To run the example in [the current article draft](docs/drafts/blog.md), start with [the Tripper decision demo](examples/tripper-decision-demo/README.md).** It includes the full-journey agent, the new point-of-interest review, and the lightweight measured city outing. Its source is derived from [Embabel Tripper](https://github.com/embabel/tripper) under Apache-2.0. The Java Decision Desk application below is a separate MIT-licensed companion using the same Embabel decision and classification API; its older synthetic coastal fixture remains available for comparison.

You have one free afternoon in an unfamiliar city. The app finds up to three mapped museum leads, reads a forecast for the proposed hour at each place, estimates a base driving time, and produces a ranked **research plan**. The joke is that the museums are real but the traveler's 45-minute patience threshold is made up. Opening hours, admission, accessibility, and live traffic are not verified.

The Embabel Agent `CityAfternoonAgent` follows `discover → assess → preferences → rank → finish`. Its `assess` action sends the same proposition, choice, and rating `DecisionRequest` and a separate `ClassificationRequest` to a named `activeDecisionService`. Change one setting, `--demo.engine=local|jev|openai`, to switch a Java rules implementation, TypeSafe JEV, or an OpenAI-backed LLM. Successful decision answers determine which leads enter the local `Ranker`; the ranker uses fictional preference weights and sourced weather and drive estimates. The goal card stays `research` because a model answer does not verify a museum visit.

## Run the article's Tripper outing

```sh
cd examples/tripper-decision-demo
mvn test package
GOOGLE_CLIENT_ID=unused GOOGLE_CLIENT_SECRET=unused java -jar target/tripper-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --tripper.outing.run=true --tripper.decision.engine=local '--tripper.outing.destination=Miami, Florida' '--tripper.outing.origin=Miami, Florida' --tripper.outing.simulated-traffic-delay-minutes=15
```

See the [Tripper example guide](examples/tripper-decision-demo/README.md) for the JEV/OpenAI switch, optional Dice graph, provenance, and limits. The separate Java app can be run as follows.

## Run the Java companion

Requires Java 21, Maven, and Embabel Agent `1.5.4-SNAPSHOT` artifacts. Public place, weather, and route APIs need no key.

```sh
mvn test package
java -jar target/decision-desk-0.1.0.jar --demo.mode=outing --demo.engine=local '--demo.destination=Miami, Florida' '--demo.origin=Miami, Florida' --demo.simulated-traffic-delay-minutes=15
```

`--demo.destination` accepts any geocodable city or place. The default forecast time is the next 2 pm in that destination's reported time zone. `--demo.window-start-utc=<ISO-8601 instant>` overrides it within the next six days. `--demo.origin` defaults to the destination center; use a public origin for shared examples. OSRM supplies a **base** driving estimate. `--demo.simulated-traffic-delay-minutes` adds an explicitly fictional 0–180 minutes (default zero); it is not real-time traffic. The fixed illustrative policy marks a lead `candidate` when the required values exist, precipitation probability is at most 50%, and base drive plus simulated delay is at most 45 minutes. `candidate` means research lead, not a confirmed plan.

For JEV, set `--demo.engine=jev` with the existing runtime `TYPESAFE_API_KEY`. For OpenAI, use `--demo.engine=openai` with `OPENAI_API_KEY`; the default supported baseline is `gpt-4.1-mini`. Pass keys through the environment only, never command arguments or project files. A bounded comparison can add `--embabel.agent.platform.decisions.llm.max-attempts=1`. One outing run makes at most three mixed decision calls and three classification calls, one per mapped option.

## Sources and limitations

`CityOutingProvider` uses [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), one user-triggered [OSM Nominatim](https://nominatim.org/release-docs/latest/api/Search/) museum search, [Open-Meteo weather](https://open-meteo.com/en/docs), and [OSRM](https://project-osrm.org/docs/v5.24.0/api/) route estimates. Place data: © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). The CLI follows the [public Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/) by making one identified search per user run; it is not a bulk or scaled place service. `--demo.nominatim-base-url` switches that endpoint. A search may return fewer than three leads or none. Geocoding, OSM, forecasting, and routing coverage differ by destination. No lead is invented when a provider has no result.

The forecast is a model at a supplied coordinate and hour. The route estimate is not a live traffic reading. Source URLs, sampling time, retrieval time, and unavailable fields are printed. Check official museum hours and accessibility before using the plan. The local ranking is application code; decision engines do not do it.

## Fictional preferences and the older coastal fixture

`--demo.dice.enabled=true` starts an app-owned disposable Neo4j through Testcontainers, writes fictional Dice 0.4.0 preference propositions, reads short-drive and low-wind weights for ranking, and stops the container on normal exit. Docker is needed for this optional path. [The seed](src/main/resources/demo-persona.properties) contains no personal data. A forced kill can bypass shutdown hooks.

`--demo.mode=compare --demo.city=Miami` exercises the original **synthetic coastal fixture**. The four-city catalog, tides, weather, traffic, and place names in that mode are fictional and are not the live city input limit. `--demo.mode=live` retains the earlier kayak research path with NOAA station context; its mapped access and local tide/current applicability remain unverified. The museum example is the recommended route through this repository.

[The Tripper live outing check](examples/tripper-decision-demo/results/city-outing-live-2026-10-11.md) separates sourced data, decisions, and measurement limits; [the Tripper decision smoke](examples/tripper-decision-demo/results/decision-smoke-2026-10-11.md) records the typed request checks. They were measured in the source Tripper working branch and are not measurements of this Java application. [The synthetic comparison](results/private-city-miami-validation.md) includes earlier per-call and process timing for Decision Desk's coastal fixture; it does not measure live provider time. [The request tour](docs/private-request-tour.md) details the original fixture API, which the city agent uses in the same fashion.

The Java companion was written separately; no Tripper source was copied into it. The Tripper example retains its upstream Apache-2.0 license in its own directory. The repository's root [MIT license](LICENSE) covers the original Java companion code.

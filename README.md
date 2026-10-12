# Bayes of Our Lives

Bayes plans a real city outing with [Embabel Agent](https://github.com/embabel/embabel-agent). The agent gathers your outing requirements, captures and retrieves confirmed interests and pace in an ephemeral [Dice](https://github.com/embabel/dice) store, researches OpenStreetMap places and base routes, proposes two timed itinerary alternatives, and asks a typed decision engine to select one or request more research. A small configuration change switches the decision implementation: local Java rules, TypeSafe Jev, or OpenAI `gpt-4.1`. The agent actions and itinerary types stay the same.

The app returns unresolved questions instead of claiming that map tags prove current hours, access, admission, food safety, or live traffic. See the [Medium-ready article draft](docs/drafts/blog.md) and [live itinerary observations](results/live-itinerary-check-2026-10-12.md). The article is not published on Medium.

## Run

Requires Java 21, Maven, and resolution of Embabel Agent `1.5.4-SNAPSHOT` and Dice `0.4.0` dependencies.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/**. See the [desktop](docs/screenshots/bayes-itinerary-desktop.png) and [mobile](docs/screenshots/bayes-itinerary-mobile.png) captures of a synthetic October 13 Miami itinerary. The server binds to loopback and starts with in-memory Dice. The browser requests `/api/itinerary`; it does not receive model keys or host paths. Enter a real city and starting location, date and time, food choices, group size, interests, pace, and optional constraints. A 7am request with no breakfast or coffee answer returns a specific question before any provider call. The form requests consent for public map lookups and separate consent for a remote decision engine.

The server reads existing `TYPESAFE_API_KEY` and `OPENAI_API_KEY` from its runtime environment only for Jev or OpenAI. Local rules need no model key. Do not put credentials in a browser field or project settings. Dietary or allergy text is held only for the request and is deliberately excluded from map and model inputs; a boolean flags the need for direct venue confirmation. Dice stores only confirmed interests and pace for this outing, and the store is discarded after the run. It does not recall prior outings.

## Evidence boundaries

Bayes uses [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) for sight candidates and origin lookup, [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md) for café/restaurant candidates, and [OSRM](https://project-osrm.org/docs/v5.24.0/api/) for base driving durations. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Public services have usage limits and can be unavailable. The app bounds each place category to three candidates and throttles Nominatim requests. No paid place search or private profile service is required.

A mapped place and route are **research evidence**, not a booking or safety guarantee. Bayes conservatively parses a small subset of OSM `opening_hours`; unsupported or absent tags remain unknown. Tagged open hours still require current confirmation. A mapped closure makes an alternative invalid. Missing meal places, unavailable routes, missing access facts, admission, cuisine, child suitability, and dietary checks remain visible questions. OSRM durations omit live traffic. Planning dwell times are illustrative rules (45–90 minutes for a sight, 20–60 for food), not measured venue visit lengths.

The full itinerary result includes every alternative, timed stops, source links, OSM tags, open questions, accepted Dice fact IDs, typed decision outcomes and provider/model provenance. A decision engine may choose `research` instead of an alternative. It can never turn an invalid alternative into a confirmed one.

## Reproduce and inspect

`mvn test package` runs model-free checks for request validation, opening-hour interpretation, itinerary scheduling, access and dietary uncertainty, and the existing decision adapters. CI runs tests without credentials. For a local, user-triggered live check, enter synthetic requirements in the browser and choose local rules; expect place availability and times to vary with public providers. [The captured October 12 check](results/live-itinerary-check-2026-10-12.md) records exact inputs, outputs, counts, failures, and timing caveats. External model requests are optional and bounded; each itinerary makes one typed decision and one classification request.

The earlier fixed-input single-lead comparison remains reproducible at `benchmarks/run-fixed.sh local|jev|openai|clef` using `--bays.cli=true`. See [fixed comparison](results/fixed-memory-comparison-2026-10-11.md). It exercises the earlier compact agent path, not the itinerary UI. Remote call times include client, network, provider, and parsing. The optional CLEF adapter is kept only for technical experiments; its [setup](clef/README.md) requires a prepared local runtime. The in-memory app requires no Docker; an optional graph-backed Dice check uses an app-owned disposable Neo4j container.

## Provenance

The itinerary action shape adapts [Embabel Tripper](https://github.com/embabel/tripper)'s brief → idea → researched point of interest → proposed plan → decision → final plan flow. See [NOTICE](NOTICE.md) and the retained [Apache 2.0 license](licenses/TRIPPER-APACHE-2.0.txt). Original Bayes work is under [MIT](LICENSE). No source or wording from private drafts is included.

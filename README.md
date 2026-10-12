# Bayes of Our Lives

Bayes plans a city outing with [Embabel Agent 1.5.4-SNAPSHOT](https://github.com/embabel/embabel-agent). The agent gathers requirements, captures confirmed interests and pace in [Dice 0.4.0](https://github.com/embabel/dice), discovers mapped places, retrieves a few venue websites, builds two timed alternatives, and asks a typed decision engine to choose an alternative or request more research. The same `DecisionSpec` and `ClassificationSpec` run through local Java rules, TypeSafe Jev, OpenAI `gpt-4.1`, or a separately prepared local CLEF runtime. The agent actions and itinerary types stay the same.

The app exposes unknowns rather than treating map tags or website snippets as proof of current hours, access, admission, food safety, or traffic. See the [Medium-ready article draft](docs/drafts/blog.md), [fixed-context itinerary comparison](results/itinerary-replay-2026-10-12.md), and [live outing observation](results/live-itinerary-check-2026-10-12.md). The article is not published on Medium.

## Run

Requires Java 21, Maven, and resolution of Embabel Agent `1.5.4-SNAPSHOT` and Dice `0.4.0` dependencies. Snapshot availability may change.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/**. See the [desktop](docs/screenshots/bayes-itinerary-desktop.png) and [mobile](docs/screenshots/bayes-itinerary-mobile.png) captures. The browser requests `/api/itinerary`; it never receives model keys or host paths. Enter a city, origin, date/window, food choices, group size, interests, pace, and optional constraints. A 7am outing with no breakfast or coffee answer returns a targeted question before any map or model call. The interface offers an answer button and resubmits the edited request. You can revise other fields and resubmit as well. A `research` result reports gaps; it does not trigger an automatic research loop.

The form asks for public map consent and separate remote model consent. Jev and OpenAI read existing runtime `TYPESAFE_API_KEY` and `OPENAI_API_KEY` respectively; do not put credentials in the browser or persistent settings. Local Java rules need no key. The optional CLEF engine requires a prepared local model/checker and bridge path supplied at runtime (`CLEF_MODEL_DIR`, `CLEF_CHECKER_PATH`, `BAYS_CLEF_BRIDGE`); see [CLEF setup](clef/README.md). Model weights and credentials are not in this repo. The bridge has bounded input length and a deadline; a context that exceeds its limit fails rather than silently truncating.

Dice captures confirmed interests and pace for each outing. With explicit **Remember preferences** consent, the official Dice JSON proposition repository saves only those two fields in `.bayes-data/preferences.json` on this machine. A browser-generated profile ID scopes retrieval, and **Use remembered preferences** lets a later outing fill them in. Without consent, capture remains in memory for that request. Today's meal, access, group, and dietary needs always come from today's form. Allergy/dietary text is excluded from public map searches and remote decision input; a flag asks for direct venue confirmation. The local preference file is ignored by Git; deleting it clears saved preferences.

## Evidence boundaries

Bayes uses [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) for sights/origin, [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md) for cafés/restaurants, and [OSRM](https://project-osrm.org/docs/v5.24.0/api/) for base driving time. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Public services have usage limits and can be unavailable. Searches are bounded to three places per category, with Nominatim throttling.

For up to six candidates, `OfficialPageResearch` independently attempts a bounded HTTPS retrieval of the OSM-recorded venue website. It records page/source/status and short clues, but does not verify facts from those clues. It refuses local/private hosts and redirects. A site can deny access; many mapped places have no website tag. The [Tripper reuse map](docs/tripper-reuse-map.md) shows the exact upstream contracts and limits of this narrower adaptation. Tripper's broader web, browser, weather, image, and Airbnb tools are not configured here.

Bayes parses only a conservative subset of OSM `opening_hours`. A mapped closure invalidates an alternative. Tagged opening hours remain provisional because tags can be stale. Missing places/routes, admission, cuisine, child suitability, access, and dietary checks remain visible questions. `wheelchair=no` invalidates a step-free option; even `wheelchair=yes` does not prove the whole route is accessible. OSRM has no live traffic; Bayes asks for a traffic check only when the time window is tight. Dwell times are illustrative rules, not measured visit lengths. A model cannot override a hard invalidity.

The result includes alternatives, timed stops, source links, tags, open questions, Dice fact IDs, typed answers, and model provenance. No alternative is a confirmed reservation or guarantee.

## Reproduce the comparison

`mvn test package` runs model-free request, memory reload/isolation, venue retrieval safety, scheduling, evidence, and decision adapter tests. CI needs no credentials. For a live local check, enter synthetic requirements in the browser; current map results will vary. Each submitted itinerary makes at most one decision and one classification call to the selected engine.

For **byte-identical decision context** across engines, run the captured itinerary replay. It exercises the real `BayesAgent.decideItinerary` action and bypasses fresh public lookup and the rest of orchestration. Run one engine at a time, with prepared runtime credentials/assets only as needed:

```sh
mvn test package
benchmarks/run-itinerary-replay.sh local
benchmarks/run-itinerary-replay.sh jev
benchmarks/run-itinerary-replay.sh openai
BAYS_REPLAY_WARMUPS=0 BAYS_REPLAY_RUNS=1 benchmarks/run-itinerary-replay.sh clef
```

The script defaults to one warmup and two measured runs (bounded to two warmups and three measured); the CLEF example uses one measured run. It verifies input hash, counts, typed outputs, and hard constraints. It writes full logs to `/tmp` by default; do not commit logs. See [measured results and limits](results/itinerary-replay-2026-10-12.md). Remote durations include adapter, network, provider work, and parsing; CLEF bridge logs also report inference and bridge wall time. This tiny illustrative run does not establish a speed ranking. The older [single-lead comparison](results/fixed-memory-comparison-2026-10-11.md) exercises a separate CLI path.

## Provenance

This app adapts [Embabel Tripper](https://github.com/embabel/tripper)'s brief → idea → researched place → proposal → review → plan shape. See the [reuse map](docs/tripper-reuse-map.md), [NOTICE](NOTICE.md), and retained [Apache 2.0 license](licenses/TRIPPER-APACHE-2.0.txt). Original Bayes code is [MIT licensed](LICENSE). No source or wording from private writing drafts is included.

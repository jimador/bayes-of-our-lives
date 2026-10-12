# Bayes of Our Lives

**Bays** is a small Java city guide built with Embabel Agent. Enter a real city, available time, and whether you want a sight or a breakfast, lunch, or dinner lead. The agent discovers mapped places and base routes, retrieves a **fictional** traveler preference from Dice, asks typed questions through a selected decision engine, and ranks only eligible answers. It returns a research lead or an explicit no-result.

The decision engine can be transparent Java rules, TypeSafe Jev, OpenAI `gpt-4.1`, or a locally prepared experimental CLEF 0.6B adapter. The agent actions and hard checks stay the same. [Read the Medium-ready article draft](docs/drafts/blog.md) and the [bounded live Miami check](results/live-miami-check-2026-10-12.md). The article has not been published on Medium.

## Run the local web app

Requires Java 21, Maven, and access to Embabel Agent `1.5.4-SNAPSHOT` and Dice `0.4.0` dependencies. Build and start:

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/**. See [desktop](docs/screenshots/bays-desktop.png) and [mobile](docs/screenshots/bays-mobile.png) screenshots of an illustrative October 12 Miami lookup with local rules and a fictional traveler. The server binds to loopback by default and starts with in-memory Dice. The browser never receives provider keys or host paths. The normal guide uses live public place data; there is no hardcoded city list. Choose a city, 30–240 available minutes, sight or meal period, fictional profile, and decision engine. The local-rules engine needs no model key. Jev and OpenAI read the existing `TYPESAFE_API_KEY` and `OPENAI_API_KEY` from the **server process environment** only. The CLEF adapter needs a prepared local runtime as described in [its technical setup](clef/README.md). An unavailable engine is reported separately from a valid lookup with no qualifying lead.

To use an alternate local port: `java -jar target/bays-0.1.0.jar --server.port=8095`. The page cancels an in-flight request and ignores stale responses when a newer request starts. Cancelling in the browser does not guarantee an already running provider call stops on the server.

## What the guide checks

The live path makes one user-triggered [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api) request, one identified [Nominatim](https://operations.osmfoundation.org/policies/nominatim/) place search, and up to three [OSRM](https://project-osrm.org/docs/v5.24.0/api/) base-route requests. Place data © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). A breakfast query looks for mapped cafes; lunch and dinner look for mapped restaurants. These queries do **not** verify meal service, menus, opening hours, or dietary safety. Some cities may yield no mapped leads.

The agent checks source, base route availability, and whether two base drives plus an **illustrative** 40-minute meal or 45-minute sight allowance fit the time budget. That allowance is a planning rule, not a venue fact. Live traffic, admission, accessibility, opening hours, cuisine, and ambience remain unverified. A literal word in a mapped name can provide a tentative interest tag; it does not establish what the place offers. Every output is a research lead.

`BayesAgent` obtains discoveries and Dice memory before assessment. `GuideMemories` reads one exact synthetic context and accepts only expected active fact IDs with typed, bounded metadata. The normalized memory fields, context, and IDs enter each `DecisionRequest`; raw proposition prose does not. An interest-match proposition, `prefer | consider | pass` choice, and `strong | possible | weak` rating drive eligibility for local ranking. A separate classification reports `indoor | outdoor | unknown` without ranking. Operational failures never become approval.

## CLI and reproducible fixtures

The CLI is available with `--bays.cli=true`, for example:

```sh
java -jar target/bays-0.1.0.jar --bays.cli=true '--bays.city=Miami, Florida' --bays.intent=sights --bays.profile=art-quiet --demo.engine=local
```

The fictional Harborview catalog is retained **only for tests and the fixed-input comparison**, not the web guide. `benchmarks/run-fixed.sh local|jev|openai|clef` runs one synthetic profile and verifies the [captured input hashes](benchmarks/fixed-art-quiet.json). See the [fixed comparison](results/fixed-memory-comparison-2026-10-11.md) for counts, typed answers, failures, and timing limits. Remote call times include client, network, provider, and parsing costs; they are not pure model inference. The benchmark invokes `--bays.cli=true` and does not start a web server.

An optional graph-backed check starts an app-owned disposable Neo4j container with `--demo.dice.enabled=true`; Docker is required for that mode. `mvn -Dbays.dice-smoke=true -Dtest=DiceGraphSmokeTest test` exercises its seed/retrieval path. The default app uses in-memory Dice and starts no persistent service. CI runs Maven and model-free CLEF bridge tests without credentials.

## Provenance

The Java guide adapts the researched-place → decision → ranked research-lead flow from [Embabel Tripper](https://github.com/embabel/tripper), inspected at `002a08c`. See [NOTICE](NOTICE.md) and the retained [Apache-2.0 license](licenses/TRIPPER-APACHE-2.0.txt). Original Bays work is under the root [MIT license](LICENSE). Earlier Decision Desk and Kotlin Tripper examples remain recoverable in Git history but are not in the current app.

# Bayes of Our Lives

**Bays** is a small Java example of Embabel Agent decisions. You wake up in a city with a little free time. Do you look at sights or find a meal? The agent gets three leads, retrieves a fictional traveler profile from Dice, asks typed preference questions through a selected decision service, and locally ranks the eligible leads. Change `demo.engine` to compare local rules, TypeSafe Jev, OpenAI `gpt-4.1`, and the [experimental CLEF 0.6B adapter](clef/README.md) without changing the agent actions. The `local` setting means transparent Java rules, not a model.

The default **fixed catalog** makes the comparison repeatable. Two profiles see the same three Harborview sights: an art gallery, a science hall, and a garden. The profiles differ in interest, meal interest, ambience, and comfortable drive time. The fixed meal catalog works the same way. Harborview and its venues are fictional.

## Run

Requires Java 21, Maven, and access to Embabel Agent `1.5.4-SNAPSHOT` and Dice `0.4.0` dependencies. The default run needs no API key, Docker, or network after dependencies are available.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
java -jar target/bays-0.1.0.jar --bays.intent=meal
```

Both commands run `art-quiet` and `science-lively` profiles over the same fixed venue facts. Select one with `--bays.profile=art-quiet` or `science-lively`. `--bays.available-minutes=90` is an illustrative time budget (allowed: 30–240). The hard time check is two base-drive estimates plus a **fictional** minimum visit duration. It does not check opening hours, admission, accessibility, actual traffic, or dietary safety.

Dice propositions are seeded and retrieved through the in-memory Dice repository by default. To check the graph-backed path, run one profile with `--demo.dice.enabled=true`; Bays starts an app-owned disposable Neo4j container and stops it on normal exit. Docker is required for that option. The normalized decision input lists the accepted Dice fact IDs and context. Raw memory prose is never passed to a decision engine.

```sh
java -jar target/bays-0.1.0.jar --bays.profile=art-quiet --demo.dice.enabled=true
```

For bounded remote runs, use the existing `TYPESAFE_API_KEY` or `OPENAI_API_KEY` in the process environment, never in source, arguments, or a settings file:

```sh
java -jar target/bays-0.1.0.jar --bays.profile=art-quiet --demo.engine=jev --embabel.agent.platform.decisions.llm.max-attempts=1
java -jar target/bays-0.1.0.jar --bays.profile=art-quiet --demo.engine=openai --embabel.agent.platform.decisions.llm.max-attempts=1
```

One selected profile asks at most three mixed proposition/choice/rating requests and three separate classifications. `demo.openai-model` defaults to **`gpt-4.1`**. [The fixed input capture](benchmarks/fixed-art-quiet.json) records candidate facts, validated memory context, and SHA-256 hashes for checking that each engine saw the same input. [The bounded observations](results/fixed-memory-comparison-2026-10-11.md) distinguish model calls from the local ranker and include failures and run counts.

## Practical city lookup

```sh
java -jar target/bays-0.1.0.jar --bays.mode=live --bays.profile=art-quiet '--bays.city=Miami, Florida' --bays.intent=sights
```

Use `--bays.intent=meal` to search for mapped restaurants instead. This path makes one user-triggered, identified [Nominatim](https://operations.osmfoundation.org/policies/nominatim/) place search, one [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api) request, and up to three [OSRM](https://project-osrm.org/docs/v5.24.0/api/) base-route requests. Place data © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Searches may find fewer than three leads or none. The only live interest tag is a **literal word in a venue name** (`art`, `science`, `garden`, `bakery`, `spicy`, or `market`); it is not verified content. Ambience, cuisine, opening hours, dietary safety, accessibility, admission, and live traffic remain unknown. A result is a research lead, not a booking or safe recommendation.

## Decision boundary

Embabel obtains `memories` and `discover` results before `assess → rank → finish`; the first two actions can run in either order. `GuideMemories` saves synthetic propositions through Dice and reads one exact context. It accepts only expected active proposition IDs with typed, bounded metadata; irrelevant, inactive, malformed, or duplicate entries cannot become prompt instructions. `BaysAgent` puts the validated interest, ambience, drive preference, context, and fact IDs into the same `DecisionRequest` for each backend. The proposition asks about an interest match, the choice is `prefer | consider | pass`, the rating is `strong | possible | weak`, and a separate `ClassificationRequest` reports `indoor | outdoor | unknown`. Classification describes setting; it does not rank.

Application code alone handles missing source/route and the illustrative time bound. It does not send hard-ineligible venues to a model. Typed failures stay visible and cannot enter the ranker as approval. `GuideRanker` orders only hard-eligible, decision-approved leads using the typed answers and a small base-drive tie break; it never rereads raw Dice memory. Remote call times include client work, network, queueing, model work, and parsing, so they are not pure inference times.

## Provenance

This Java guide adapts the researched-place → decision → ranked research-lead flow from [Embabel Tripper](https://github.com/embabel/tripper) (base inspected at `002a08c`). See [NOTICE](NOTICE.md) and the retained [Apache-2.0 license](licenses/TRIPPER-APACHE-2.0.txt). Original Bays code is under the root [MIT license](LICENSE). The earlier Decision Desk coastal and Kotlin examples remain recoverable in this repository's Git history, most recently at `e56b17b`; they are no longer part of the current app. The article draft is being rewritten for Bays and is not yet in this tree.

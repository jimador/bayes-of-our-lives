# Tripper decision smoke, 2026-10-11 UTC

A **synthetic** single-POI fixture was fed to Tripper's actual `PointOfInterestQuestions` contract. It named an art museum in Miami, one illustrative source URL, an overlapping date, and an indoor description. The same mixed proposition/choice/rating `DecisionRequest` and separate `ClassificationRequest` were sent through each implementation. This checks typed integration, not real Tripper place discovery or travel quality.

| Engine | Choice | Setting | Mixed decision call | Classification call | Runs | Warmups | Typed failures |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| Local Kotlin rules | prioritize | indoor | 0.084 ms | 0.107 ms | 1 | 0 | 0 |
| TypeSafe JEV | prioritize | indoor | 465.952 ms | 200.443 ms | 1 | 0 | 0 |
| OpenAI `gpt-4.1-mini` | prioritize | indoor | 1138.163 ms | 430.056 ms | 1 | 0 | 0 |

The local test checks exact proposition, choice, rating, and classification values. The remote smoke tests record choice and classification and assert that neither returned `UNAVAILABLE`; they do not assert exact remote proposition or rating agreement. The table therefore reports only the observed choice and classification. The remote tests used the existing runtime `TYPESAFE_API_KEY` and `OPENAI_API_KEY`, with no credential values printed or written. Each remote test made one mixed decision request and one separate classification request. No loop, training, or large batch was run.

The clocks enclose only the client call, not JVM/Spring startup, the full agent process, discovery, or ranking. JEV/OpenAI times include network, provider queueing, model work, and parsing; these components were not separately observed. The local number has no network leg. The table selects one un-warmed passing invocation per engine; local tests were repeated during development. These are **illustrative observations, not provider performance comparisons**. Availability, model versions, and answers may change.

Reproduce after setting the required keys in the process environment (never in files):

```sh
mvn -q test
mvn -q -Dtest=JevDecisionSmokeTest -Dtripper.external-smoke=true test
mvn -q -Dtest=OpenAiDecisionSmokeTest -Dtripper.external-smoke=true test
```

The default `mvn test` skips both network tests and runs the local typed checks. This branch also passed `mvn -q package` and Spring startup without a travel request in each engine mode. A full Tripper journey was not run: its existing web/maps/weather, image, lodging, and OAuth integrations need their own setup. Do not label these timings as end-to-end trip planning results.

The default Spring context test deployed `TripperAgent` with Dice disabled and no graph container. Separately, `mvn -q -Dtest=DicePreferenceSmokeTest -Dtripper.dice-smoke=true test` wrote and read the fictional `art`/0.15 preference in an app-owned Neo4j container (1 test passed, about 61 seconds on this host); the container was no longer running after the context closed. The pure review-path test verifies that a review/skip answer is excluded from planner text and that the optional Dice value adds exactly 0.15 to the eligible art item's local score. These are fixture and integration checks, not a full live journey.

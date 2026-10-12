# Bounded live itinerary check — 2026-10-12

One synthetic, nonpersonal Miami outing was run through the actual Embabel Agent 1.5.4-SNAPSHOT itinerary actions and Dice 0.4.0 in-memory capture/retrieval. This records an earlier build's live observation; the current build adds optional persistent preferences, bounded venue-page retrieval, CLEF itinerary selection, and a conditional traffic question. It is an illustrative smoke check, not a latency benchmark. Public map data may change. No actual traveler's profile was used.

## Input and procedure

City `Miami, Florida`; start `Downtown Miami`; date `2026-10-13` (Tuesday); window `07:00–14:00`; breakfast and lunch requested; coffee and dinner not requested; two adults; interest `art`; steady pace; no step-free or dietary constraint. Public map consent true. Three sequential calls used the same stated requirements with engines `local`, `jev`, and `openai`. Remote consent true for the latter two. Existing runtime environment keys were used; no key values were printed or saved. Each call found live OSM places and OSRM base routes, constructed alternatives, then asked its decision engine. No warmup was done. One run per engine, no retries. No statistical inference is possible.

## Observations

| Engine | Result | Typed choice | Decision call | Classification call | Whole request wall clock |
| --- | --- | --- | ---: | ---: | ---: |
| Local Java rules | unresolved | B | 7.9 ms | 0.1 ms | 5.26 s |
| TypeSafe Jev `jev-1.13.0` | needs_research | research | 401.3 ms | 175.5 ms | 3.9 s |
| OpenAI `gpt-4.1` | needs_research | research | 1,938.7 ms | 983.2 ms | 6.53 s |

HTTP 200 and complete typed outputs were observed for all three; zero transport or typed-response failures in these three calls. The local run's alternative A had breakfast at Cafe Galleria, Pérez Art Museum Miami, and lunch at Versailles Restaurant. A was **invalid**: the museum's OSM `opening_hours` tag marked Tuesday closed. Alternative B had Cafe Con Leche, Patricia & Phillip Frost Art Museum, and El Cartel Colombian Restaurant. B remained **unresolved** because current hours and museum admission were not verified. That earlier build also asked for a live traffic check unconditionally; the current planner asks only when schedule slack is tight. The decision outputs differ: local rules selected the tentative B; both models requested more research. Neither choice confirms the itinerary.

The three decision inputs were not byte identical. Dice created a fresh context ID for each request, and public provider results and base route times can vary. Their SHA-256 hashes were `c01faf9197317f88222c65fa2387d8580016fdb7bd4da5e8a0a142398f6b6e72` (local), `d7208aca7abd1c79fb926045568fa0a7a5048d6e036e352073973edc05a365a4` (Jev), and `b5a69e5fc656f629354f46af47023e4a4568c836e9fafe7f41f5b6f5212e48df` (OpenAI). The call durations include the adapter, network, provider processing, and parsing; they are not model inference times. Whole request times also include map lookup and agent overhead. The order and provider cache/network behavior were uncontrolled. These values do not support a speed ranking.

## Reproduce

Build with `mvn test package`, run `java -jar target/bays-0.1.0.jar`, and submit the stated synthetic input in the browser. Local rules need no key. Set only runtime `TYPESAFE_API_KEY` or `OPENAI_API_KEY` to try the remote engines and check their consent box. Use one request per engine; avoid load testing public map servers. Results are expected to differ with map edits, provider availability, date, and route conditions. The earlier controlled fixed-input decision comparison is in [fixed-memory-comparison-2026-10-11.md](fixed-memory-comparison-2026-10-11.md), but uses the older single-lead CLI path.

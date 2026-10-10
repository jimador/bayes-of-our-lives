# Should I go paddling today? One agent, three decision services

A real Embabel Agent **1.5.4-SNAPSHOT** process combines a synthetic marine forecast, tide-height prediction, separate tidal-current prediction, and simulated drive time to a fictional launch. It sorts a proposed coastal kayaking window into `candidate`, `review`, or `defer`. The same agent action calls `ai.decisions().named("activeDecisionService").ask(...)` with TypeSafe Jev, an OpenAI-backed LLM decision service, or an application-owned rules service. `demo.engine` swaps the service; the agent's work stays the same.

**This is a wiring and decision-contract demo, not a marine safety tool.** The location, launch access rule, observations, predictions, road estimates, and numeric policy bands are fictional. `candidate` means only “worth a human look”; it is never a safe-to-launch verdict. No live forecast, tide station, current station, road API, or actual location is used.

## What the agent does

1. `prepare` combines the fixture values, computes forecast age and window coverage, and asks [`SimulatedTrafficProvider`](src/main/java/dev/jimador/decisiondesk/SimulatedTrafficProvider.java) for a fixed drive delay and arrival time. All timestamps are UTC.
2. `choose` asks one typed Embabel choice question. Unknown or stale input maps to `review`; a fictional storm or exceeded numeric band, or arrival after the window start, maps to `defer`; otherwise the output is a `candidate` for human review.
3. `finish` makes a planning card. A deterministic [`CoastalPolicy`](src/main/java/dev/jimador/decisiondesk/CoastalPolicy.java) guard checks any model-selected `candidate` against the same recorded facts and changes it to `review` if the illustrative rule does not agree. The CSV retains the raw model choice and the guarded card choice separately.

The weather inputs include sustained wind and gust in knots, swell in feet, and a storm signal. The tide input is a predicted **water height in feet above MLLW** and a phase at 16:00 UTC. The tidal-current input is a separate predicted **speed in knots** at a separate fictional current station. [NOAA explains that high/low tide and slack current do not have a universal timing relationship](https://tidesandcurrents.noaa.gov/faq.html); this demo does not infer current from tide phase. One prediction instant cannot describe an entire two-hour window. The height band here also assumes a fictional launch access rule and has no meaning at a real ramp.

## Run

Requirements: Java 21 and Maven, with access to Embabel's snapshot repository for **1.5.4-SNAPSHOT**. The local engine needs no API key. Embabel still requires a registered LLM provider at startup, so local and Jev modes without an OpenAI key register an inert placeholder; this agent does not call it in those modes.

```bash
mvn test
./scripts/run-bench.sh local
./scripts/run-bench.sh jev
./scripts/run-bench.sh openai
```

Set `TYPESAFE_API_KEY` for Jev and `OPENAI_API_KEY` for OpenAI in the existing runtime environment. Do not place keys in source, arguments, or a file. The script runs ten synthetic cases once for warmup and once for measurement: 20 decision calls per engine. `demo.openai-model` defaults to `gpt-4.1-mini`. Jev uses Embabel's `jev-latest` alias; the CSV records its resolved model.

```bash
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.warmups=1 --demo.runs=1
```

The runner caps warmups plus measured rounds at ten. It writes no prompts or provider payloads to disk. See [raw results and limitations](results/README.md) and the [Medium-ready article draft](docs/drafts/blog.md). The article has not been published on Medium.

## Measurement and validity

- `decision_ms`: wall time around `DecisionService.ask(...)`. For remote services, this includes client work, network, provider queueing, inference, and parsing, so it is **not** model-only latency.
- `process_ms`: wall time around `AgentProcess.run()`, including preparation, choice, guard, and card creation. JVM and Spring startup are excluded.
- `raw_correct`: raw selected label agrees with the fixture's hand label under this illustrative policy. `valid`: the process completed with a legal selected label and a nonempty card. `guarded`: a raw `candidate` was changed to `review` by the deterministic check. None of these establish outdoor safety or real-world accuracy.

## Adapting to a real coast

This repository intentionally has **no live provider** because no coastal location, marine forecast zone, water-level station, current station, datum applicability, or launch access rule was specified. A real adapter would need those explicit local choices, source timestamps and units, forecast coverage, station relevance, missing-data handling, and local validation before passing a brief to the decision step. [NWS marine forecasts and warnings](https://www.weather.gov/safety/safeboating-marine), the [NWS API](https://www.weather.gov/documentation/services-web-api), and [NOAA CO-OPS observations and predictions](https://api.tidesandcurrents.noaa.gov/api/prod/) are primary starting points, not drop-in proof that a window is safe. The final judgment belongs to a person with current local information.

Embabel Agent is an Apache-2.0 dependency; this repository's original demo code is MIT licensed.

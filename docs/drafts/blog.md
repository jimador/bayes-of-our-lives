# Should I Go Paddling Today? The Tide Is Not the Current

*One Embabel agent, three decision services, and a fictional drive to a fictional launch.*

I live by the ocean, so “Should I go kayaking?” is not only a question about whether I can find the paddle. Weather matters. Tides matter. Tidal current is its own thing. And if traffic turns a short drive into a late arrival, the nice window I was looking at may have passed while I was still listening to the same song at a red light.

That is a better agent example than classifying “I feel like paddling” into yes or no. It gives the agent a few pieces of work to coordinate: read a forecast window, keep tide height separate from current speed, account for the trip to the launch, ask one narrow decision question, and make a card a person can review.

This is still a **software demonstration**, not a marine safety tool. I did not choose a real coast or station. The inputs are synthetic, the road delay is simulated, and every numeric band below is invented for the example. A `candidate` is a candidate for *human review*, not permission to launch. The [runnable project](https://github.com/jimador/embabel-decision-desk) uses Embabel Agent **1.5.4-SNAPSHOT** and includes the fixtures, source, and raw run results.

## Tide height has a separate job from current

The sample record has a proposed 15:00–17:00 UTC window. Its weather forecast carries an issue time, a valid-through time, wind, gust, swell, and a storm signal. The tide record has a predicted water height in feet above MLLW and a rising or falling phase at 16:00 UTC. A *separate* fictional current station has a predicted speed in knots at that time. Those units and timestamps are in the decision input, not left to the model to guess.

That separation matters. [NOAA says high and low tide do not generally coincide with slack current](https://tidesandcurrents.noaa.gov/faq.html); it produces tide-height and tidal-current predictions separately. “Rising tide” is not a current-speed measurement. One prediction at 16:00 also tells us little about the whole two-hour window. The example keeps those limitations visible instead of turning a tide table into an imaginary current sensor.

A tiny `SimulatedTrafficProvider` adds a fixed base drive time and delay for each case. Leaving at 14:00 UTC might get the person to the launch at 14:40, right at 15:00, or at 15:15. There is no live map, account, or real route. It is enough to make arrival time affect the proposed window.

## Keep the agent; change the decision service

The Embabel agent has three actions. `prepare` assembles the normalized weather, tide, current, and traffic brief and calculates forecast age and coverage. `choose` asks a typed choice question. `finish` produces a planning card. Embabel's process planner runs them over its blackboard. The choice call is the same for every engine:

```java
var response = ai.decisions()
    .named("activeDecisionService")
    .ask(brief.text(), WINDOW);
```

The choice specification says `review` first for missing, stale, future-dated, or incomplete input. Otherwise it says `defer` for a storm signal, a value outside an illustrative band, or traffic arriving after the window starts. Otherwise it says `candidate`. An exact boundary is in the band. These are made-up software-test numbers, **not** kayaking limits or a general launch policy.

Change `demo.engine` from `local` to `jev` or `openai` and the `activeDecisionService` bean changes. The TypeSafe path is Embabel's `TypeSafeModelFactory`; the OpenAI path is Embabel's `LlmDecisionServiceFactory`; the local path implements `DecisionService` with ordinary code. Jev's adapter accepts the caller-defined choice labels and answered this structured question in the recorded run. It is not a certified coastal policy evaluator. The LLM is not one either.

There is one deliberately unromantic guard in the agent. Before any model-selected `candidate` reaches the card, deterministic code checks it against the same recorded facts. If the code does not agree, the card becomes `review`. The raw model choice stays in the results. This means the decision engines remain interchangeable, but a model cannot silently turn an out-of-band fixture into a candidate card.

## What the small run found

I used ten synthetic cases: a plain candidate, one exactly on the numeric and arrival boundaries, gust and storm cases, low water height, stronger current, a stale forecast, a missing current prediction, a late drive, and an unknown drive delay. Each engine got one ten-case warmup pass and one measured pass. The keys came from the existing runtime environment and were not stored in the project. `jev-latest` resolved to `jev-1.13.0`; OpenAI used `gpt-4.1-mini`.

| Decision implementation | Raw policy match | Valid cards | Guarded candidates | Median decision call | Median whole process |
| --- | ---: | ---: | ---: | ---: | ---: |
| Local coastal rules | 10/10 | 10/10 | 0 | 0.166 ms | 3.397 ms |
| Jev 1.13.0 | 10/10 | 10/10 | 0 | 173.252 ms | 178.513 ms |
| GPT-4.1 mini | 8/10 | 10/10 | 2 | 483.010 ms | 487.502 ms |

The LLM selected `candidate` for the low-water and late-traffic cases, in warmup and measurement. Both violated this example's policy, and both cards became `review`. That is a more interesting result than a perfect table. `Valid` here means the agent completed with a legal selected label and a card; it does not mean the label was correct or the water safe. These ten hand-built cases are a boundary check, not an accuracy study.

The decision timer wraps `DecisionService.ask(...)`. Remote time includes client work, network, provider queueing, inference, and parsing, so it is not pure model latency. The whole-process timer wraps `AgentProcess.run()` and excludes JVM and Spring startup. The [raw CSV rows and method](https://github.com/jimador/embabel-decision-desk/tree/main/results) are more useful than a speed ratio. I did not download a local Jev model; “local” means the application-owned rules implementation.

## What I would need before using a real launch

A real version would need an actual marine forecast zone, relevant water-level and current stations, a verified datum and launch-access relationship, source times and uncertainty, and local knowledge. [NWS warns that marine forecasts cover broad areas and conditions can differ from the range](https://www.weather.gov/safety/safeboating-marine). [NOAA's CO-OPS API](https://api.tidesandcurrents.noaa.gov/api/prod/) makes observations and predictions available by station, but it does not pick the right station or establish a safe route for me. Current conditions, warnings, equipment, skill, and local judgment would remain outside this little agent's authority.

The useful result is the seam. One agent can organize different inputs, ask the same typed question through different decision implementations, and produce a reviewable card. The two wrong raw LLM choices are a reminder to keep the card's authority smaller than the model's confidence. The ocean does not care how elegant my dependency injection is.

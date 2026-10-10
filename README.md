# The Stage Desk: one Embabel agent, three decision services

At a community theater, the dragon head has jammed, a microphone is screaming, and a scenery cart is blocking an exit. The agent's job is to turn each short ticket into a dispatch card: primary crew plus a concrete first checklist. The decision step can use TypeSafe Jev, an OpenAI prompted decision service, or an application-owned keyword service. The same three `@Action` methods run in every case.

This is a small executable example of Embabel Agent **1.5.4-SNAPSHOT**'s experimental `DecisionService` API. It calls `ai.decisions().named("activeDecisionService").ask(...)` inside a real agent action. The bean named `activeDecisionService` is selected by `demo.engine`; the planner and card-building actions do not change. A selected crew is used only when the decision result is valid. Other outcomes go to a human review card.

## Run

Requirements: Java 21, Maven, network access for Maven's public Embabel snapshot and Spring repositories. The local mode needs no API key.

```bash
mvn test
./scripts/run-bench.sh local
TYPESAFE_API_KEY=... ./scripts/run-bench.sh jev
OPENAI_API_KEY=... ./scripts/run-bench.sh openai
```

Set credentials in your existing runtime environment instead of putting them on a command line or in a file. The commands above show variable names only. The script runs one warmup and one measured pass of six synthetic tickets (12 calls per engine), saves CSV under `results/`, and prints a summary. `demo.openai-model` defaults to `gpt-4.1-mini`; change it with `--demo.openai-model=...` when invoking the jar directly. Jev uses Embabel's `jev-latest` alias and the CSV records the resolved model name.

```bash
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.warmups=1 --demo.runs=1
```

`demo.warmups + demo.runs` is capped at ten to prevent accidental large runs. The runner writes no prompts or provider payloads to disk. All cases are synthetic.

## What is measured

- `decision_ms`: wall time around the public `DecisionService.ask(...)` call. For remote services this includes client work, network, provider queueing, inference, and response parsing. It does **not** isolate model execution.
- `process_ms`: wall time around `AgentProcess.run()`. It includes the decision plus local Embabel planning and the two card-building actions. JVM startup and Spring initialization are outside this timer.
- `process_ms - decision_ms`: a rough residual for local orchestration and other work, not a controlled overhead benchmark.
- `valid`: process completed, selected a crew, and produced a nonempty checklist. `correct`: chosen crew matches the hand-labeled primary crew for that synthetic ticket.

The data and limitations are in [results/README.md](results/README.md). The [article draft](docs/drafts/blog.md) is ready for editing or pasting into Medium; it has not been published there.

## Source map

- [`StageDeskAgent`](src/main/java/dev/jimador/decisiondesk/StageDeskAgent.java): real Embabel agent and typed choice request.
- [`DeskApplication`](src/main/java/dev/jimador/decisiondesk/DeskApplication.java): one configuration switch for the three services.
- [`KeywordDecisionService`](src/main/java/dev/jimador/decisiondesk/KeywordDecisionService.java): the application-owned black-box example. It deliberately supports only choice questions.
- [`DeskRunner`](src/main/java/dev/jimador/decisiondesk/DeskRunner.java): bounded synthetic cases and measurements.

Embabel Agent is an Apache-2.0 dependency; this repository contains original demo code under MIT. TypeSafe Jev and OpenAI are remote services used through their APIs. No Embabel or private project source was copied into this repository.

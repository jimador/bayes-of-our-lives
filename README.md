# Should I go paddling today? One Embabel agent, three decision services

This is a small executable example of Embabel Agent **1.5.4-SNAPSHOT**'s experimental `DecisionService` API. A real three-action agent turns a short, synthetic kayaking preference note into a personal day card. It asks a narrow question: does the writer want to paddle today, plan a later outing, or take a day off? The same agent and choice specification run with TypeSafe Jev, an OpenAI-backed LLM decision service, or an application-owned rule service. `demo.engine` changes only the registered decision service.

**This is not a kayaking safety or weather tool.** Its notes contain no live conditions, it has no validated launch thresholds, and its labels are preferences rather than real go/no-go advice. A `paddle` card tells the person to check current conditions and make their own decision.

The agent calls `ai.decisions().named("activeDecisionService").ask(...)` from an `@Action`, prepares a next-step card in a second action, and finishes through `@AchievesGoal`. Invalid or inconclusive decisions produce a review card instead of an invented preference.

## Run

Requirements: Java 21 and Maven. Maven needs access to Embabel's snapshot repository for 1.5.4-SNAPSHOT. Local mode needs no API key. Embabel still requires a registered LLM provider at startup, so local and Jev modes without an OpenAI key register an inert placeholder; this agent never calls that model in those modes.

```bash
mvn test
./scripts/run-bench.sh local
./scripts/run-bench.sh jev
./scripts/run-bench.sh openai
```

Set `TYPESAFE_API_KEY` for Jev and `OPENAI_API_KEY` for OpenAI in the existing runtime environment. Do not place keys in source, arguments, or a file. The script uses one six-note warmup pass and one six-note measured pass per engine, writes CSV under `results/`, and prints a summary. `demo.openai-model` defaults to `gpt-4.1-mini`. Jev uses Embabel's `jev-latest` alias; each CSV row records the resolved model.

To run the built jar directly:

```bash
java -jar target/decision-desk-0.1.0.jar --demo.engine=local --demo.warmups=1 --demo.runs=1
```

`demo.warmups + demo.runs` is capped at ten rounds. The runner writes no prompts or provider payloads to disk. Every note is synthetic.

## Measurement and validity

- `decision_ms` wraps Embabel's public `DecisionService.ask(...)` call. Remote time includes client work, network, provider queueing, inference, and response parsing. It is not isolated model latency.
- `process_ms` wraps `AgentProcess.run()`, including the decision, Embabel planning, and the two card actions. JVM and Spring startup are excluded.
- `process_ms - decision_ms` is a rough local residual, not a controlled orchestration benchmark.
- `valid` means the process completed with a selected choice and nonempty next steps. `correct` means its choice matched our hand label for an explicit synthetic note. Neither tests outdoor safety or performance on real diary entries.

See [raw rows and limitations](results/README.md) and the [Medium-ready article draft](docs/drafts/blog.md). The article has not been published on Medium.

## Source map

- [`PaddleDayAgent`](src/main/java/dev/jimador/decisiondesk/PaddleDayAgent.java): agent actions and typed preference choice.
- [`DeskApplication`](src/main/java/dev/jimador/decisiondesk/DeskApplication.java): the one-property engine switch.
- [`PreferenceDecisionService`](src/main/java/dev/jimador/decisiondesk/PreferenceDecisionService.java): deliberately narrow application-owned choice implementation.
- [`DeskRunner`](src/main/java/dev/jimador/decisiondesk/DeskRunner.java): bounded synthetic cases and timers.

Embabel Agent is an Apache-2.0 dependency; this repository's original demo code is MIT licensed. TypeSafe Jev and OpenAI are remote services accessed through their APIs.

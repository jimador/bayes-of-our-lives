# The Dragon Head Broke. Which Model Gets the Call?

*One Embabel agent, three interchangeable decision services, and a very small theater emergency.*

At 6:55, the dragon head jams before its entrance. A microphone starts feeding back. Somebody has parked a scenery cart in front of the exit. The stage manager has about five minutes to decide who deals with what, and even less patience for a chatbot composing a stirring paragraph about teamwork.

This is a useful shape for an agent demo. The incoming text is messy. The next decision is narrow: send the ticket to props, sound, or safety. Once that decision is made, the rest of the work is ordinary software. Build a dispatch card, attach a first checklist, and give the crew something they can use.

I wanted to see what happens when that one decision changes implementation while the agent stays put. In [Embabel Agent](https://github.com/embabel/embabel-agent) 1.5.4-SNAPSHOT, the experimental `DecisionService` API makes that possible. I put the same stage-desk agent behind three implementations: TypeSafe's Jev, an OpenAI-backed prompted decision service using `gpt-4.1-mini`, and a tiny keyword classifier standing in for an application-owned system.

The [runnable project](https://github.com/jimador/embabel-decision-desk) includes the source, synthetic cases, raw CSVs, and a script to repeat the run. The article you are reading lives in that repository too.

## The agent does more than classify

The agent has three Embabel actions. `route` asks a typed choice question. `prepare` turns the selected crew into a checklist. `finish` produces a dispatch card and achieves the goal. Embabel's process planner runs those actions over the blackboard. This is an actual agent process, not a loop that imitates one.

The decision is deliberately small:

```java
private static final DecisionSpec ROUTE = DecisionSpec.builder()
    .choice("crew", q -> q.asking("Which crew should handle this theater issue first?")
        .option("props", "Props and set pieces")
        .option("sound", "Speakers, microphones, monitors")
        .option("safety", "Exits, fire, injury, electrical hazards"))
    .build();

var response = ai.decisions()
    .named("activeDecisionService")
    .ask(ticket.note(), ROUTE);
```

The source has the full option descriptions and the outcome handling. The important part is that the agent asks through Embabel's public decision-service selector. It does not call Jev's HTTP endpoint directly or ask an LLM to emit an unvalidated string. A selected category becomes a crew. A failure, no match, or inconclusive result becomes a human-review card. The model supplies evidence for the route; code controls what to do with it.

The switch is a single property:

```properties
demo.engine=local
```

Change that to `jev` or `openai`, and the `activeDecisionService` bean changes. The agent's actions, decision spec, and dispatch-card logic stay the same. Jev is built through Embabel's `TypeSafeModelFactory`; the OpenAI path uses Embabel's `LlmDecisionServiceFactory`; the local service implements `DecisionService` itself. The local one is intentionally unsophisticated. It looks for safety words first, then sound words, and sends everything else to props. That is enough to show that the extension point can hold a custom or black-box decision system. It is not a claim that regexes can run a theater.

## What happened on six tickets

I ran six short, synthetic tickets: two props problems, two sound problems, and two safety problems. Each implementation got one warmup pass and one measured pass, six tickets per pass. That is 12 calls per implementation, 36 total. The keys came from the existing runtime environment and were not written into the project. Jev's `jev-latest` alias resolved to `jev-1.13.0`; the OpenAI model was `gpt-4.1-mini`.

All three implementations routed all six measured tickets to the hand-labeled primary crew. All 18 measured agent processes completed with a nonempty checklist. There were no recorded failures. That is reassuring for this example, but six straightforward tickets are not an accuracy benchmark.

| Decision implementation | Correct / valid, measured | Median decision call | Median whole process |
| --- | ---: | ---: | ---: |
| Local keywords | 6/6 · 6/6 | 0.188 ms | 4.178 ms |
| Jev 1.13.0 | 6/6 · 6/6 | 162.610 ms | 166.040 ms |
| GPT-4.1 mini | 6/6 · 6/6 | 823.324 ms | 829.195 ms |

Those are wall-clock times on one macOS arm64 machine running Java 21. The decision timer wraps `DecisionService.ask(...)`. For a remote model, it includes client work, network, provider queueing, inference, and response parsing. I cannot pull “model speed” out of that number. The whole-process timer wraps Embabel's `AgentProcess.run()` and excludes JVM and Spring startup. The difference between the two medians was only a few milliseconds here, but subtracting them is not a rigorous orchestration benchmark either. The [raw rows and method](../../results/README.md) are more useful than any ratio I could put in a headline.

I also did not download a local Jev-style model. The official Hugging Face ecosystem may make that an interesting follow-up, but a large model download would have turned this small example into a hardware and runtime experiment. Here, “local” means the application-owned keyword service.

## Why this boundary matters

The interesting result is architectural. The agent has a stable job: take a ticket, choose a crew, prepare first actions, produce a card. It does not need to know whether its narrow judgment came from a remote decision model, a prompted language model, or code somebody wrote years ago and now calls “the legacy rules engine” with a mixture of affection and fear.

Each implementation has a different shape. Jev returns a typed decision through Embabel's TypeSafe integration. The OpenAI service uses an LLM to answer the same Embabel question. The local classifier is transparent and fast on the words it recognizes, and brittle outside that tiny vocabulary. Embabel gives the agent one contract for all three, including explicit failure outcomes. That makes swapping the decision step a configuration choice instead of a rewrite of the workflow.

It also keeps the job of deciding what happens *after* a judgment in application code. A safety route produces a stop-and-verify checklist. A sound route starts by muting the affected channel. If the decision service cannot make a valid selection, the card goes to a person. Nobody wants the dragon head to become the stage manager just because it came back with high confidence.

I would not choose a production decision system from this table. I would start by identifying the small judgments inside an agent, write down the valid answers and failure policy, and then test candidate services on real, reviewed examples. This project is the first step: a complete runnable path showing that the boundary works, and enough measurement to see what happened without pretending it settles performance or quality.

# Should I Go Paddling Today? Ask the Decision Engine, Not the Entire Agent

*One Embabel agent, three interchangeable decision services, and six entirely fictional kayaking notes.*

I like kayaking. I do not like checking the same half dozen things, getting distracted by a promising river photo, and then discovering that what I actually wanted was to sit at home with a book. The hardest question some days is not “Which boat?” It is “Do I even feel like going?”

That makes a decent little software problem, provided we keep it honest. I am not building a robot river guide. The example has no live weather, water levels, local access rules, or safety thresholds. It reads a synthetic note about what somebody *wants* to do and turns it into a personal day card: paddle today, plan a future outing, or take a day off. Even a “paddle” card says to check current conditions and make your own go/no-go decision.

I wanted to use that narrow judgment to try the experimental `DecisionService` API in [Embabel Agent](https://github.com/embabel/embabel-agent) **1.5.4-SNAPSHOT**. The question was whether I could keep the agent's job fixed and replace only the thing that answers its decision. I tried three implementations: TypeSafe's Jev, an OpenAI-backed decision service using `gpt-4.1-mini`, and a small application-owned rules class. The [runnable project](https://github.com/jimador/embabel-decision-desk) has the code, raw measurements, and this draft.

## Give the agent a job

The agent has three real Embabel actions. `choose` asks what the note says the person wants. `prepare` builds a few next steps for that choice. `finish` produces the day card and achieves the goal. Embabel's process planner runs the actions over its blackboard. This is an agent process, not a hand-written loop dressed up as one.

The decision has three legal answers:

```java
private static final DecisionSpec PREFERENCE = DecisionSpec.builder()
    .choice("day", q -> q.asking(
            "What does this person want to do with kayaking today? " +
            "Classify their stated preference, not weather or water safety.")
        .option("paddle", "They want to paddle today and have time for it")
        .option("plan", "They want to research or plan a future kayak outing, but not paddle today")
        .option("rest", "They want a day off from kayaking and kayak planning"))
    .build();

var response = ai.decisions()
    .named("activeDecisionService")
    .ask(note.text(), PREFERENCE);
```

The important line is the call through Embabel's public decision selector. The agent neither calls Jev's endpoint itself nor asks a language model to print a word and hopes the word is valid. A selected category becomes a card. An inconclusive or failed result becomes a review card. Code decides what happens after the judgment.

The engine switch is a property:

```properties
demo.engine=local
```

Set it to `jev` or `openai` and the `activeDecisionService` bean changes. The agent actions, choice spec, notes, and card logic stay put. The Jev path uses Embabel's `TypeSafeModelFactory`; the OpenAI path uses `LlmDecisionServiceFactory`; the local path implements `DecisionService` directly. It is gloriously boring code: look for rest language, then future-planning language, then assume the note is about paddling. It is also brittle outside this tiny vocabulary. The point is that an application-owned decision system can occupy the same seam.

Jev's Embabel adapter accepts caller-defined choice labels, so this preference-routing question is a supported use of the API. That does not mean Jev understands real paddling conditions, nor does a correct classification make the plan safe. I am asking it to label a sentence, not assess a river.

## Six notes, three engines

I wrote six synthetic notes with explicit intent: two people want to paddle, two want to plan another day, and two want a day off. Each engine got one six-note warmup pass and one six-note measured pass. The keys came from the existing runtime environment; none were saved in the repository. Jev's `jev-latest` alias resolved to `jev-1.13.0`. The OpenAI model was `gpt-4.1-mini`.

All three chose the hand label for all six measured notes. All 18 measured processes finished with selected labels and nonempty cards. There were no recorded failures. These notes practically wave their answers in your face, so 6/6 says the wiring works. It says very little about general classification quality.

| Decision implementation | Correct / valid, measured | Median decision call | Median whole process |
| --- | ---: | ---: | ---: |
| Local preference rules | 6/6 · 6/6 | 0.064 ms | 1.879 ms |
| Jev 1.13.0 | 6/6 · 6/6 | 165.366 ms | 169.137 ms |
| GPT-4.1 mini | 6/6 · 6/6 | 794.429 ms | 798.740 ms |

These are wall-clock measurements on one macOS arm64 machine with Java 21. The decision timer wraps `DecisionService.ask(...)`. For remote services, it includes client work, network, provider queueing, inference, and response parsing. I cannot extract “model speed” from that number. The process timer wraps `AgentProcess.run()` and excludes JVM and Spring startup. The [raw CSV rows and method](https://github.com/jimador/embabel-decision-desk/tree/main/results) are there if you want to see each call, including warmup. I did not download a local Jev model. Here, “local” means the rules class.

## The useful seam

The interesting part is the boundary. The agent receives a note, gets one typed answer, prepares a card, and finishes its job. It does not care whether the answer came from a remote decision model, a prompted LLM, or code that might one day be replaced by a spreadsheet somebody insists is a rules engine.

The boundary also makes failure visible. A selected label is one outcome, not a command to pretend certainty. If the decision service cannot select, the card asks for review. And even when it selects `paddle`, the card does not tell anyone to launch. It asks the person to check current conditions, access rules, and their own judgment. A demo about preferences has no business making promises about water.

I would not pick a production decision system from six examples or the timing table. I would start with a real, reviewed set of notes, define the acceptable labels and failure policy, and then see how each implementation behaves on ambiguous language. This small project establishes the wiring: one Embabel agent, three real decision-service implementations, a configuration switch, and enough recorded evidence to tell what happened this time.

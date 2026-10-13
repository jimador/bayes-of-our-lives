# Bayes of Our Lives: Decision Models in Embabel Agent

Get stuck somewhere you never planned to visit and a language model will happily read venue pages and write you an itinerary. Picking between two of those itineraries is a different job. You need to know which one fits what you already know about the day, and when to stop and go find more evidence. TypeSafe built [Jev](https://typesafe.ai/blog/introducing-system-one-models-and-jev) for that kind of question, structured decisions with declared answer types. Decision models are getting enough attention that [Hugging Face added a decision-model tag and filter](https://huggingface.co/changelog/decision-models-on-the-hub) to the Hub.

[Embabel Agent](https://github.com/embabel/embabel-agent) is a JVM framework for building agents from actions and goals. It works with general language models and uses [Goal-Oriented Action Planning](https://docs.embabel.com/) (GOAP) to choose a path through those actions. Embabel Agent recently added support for typed decision and classification models through a `DecisionService` interface. An action can ask a bounded question and get back a typed result. The model behind that interface is configurable, so the same `DecisionService` can be served by a traditional LLM, a decision model like Jev, or deterministic rules. GOAP chooses the agent's actions, and the decision service answers questions inside them. Since switching the backend is a configuration change, it is also a simple way to test the performance claims for models like Jev against a traditional LLM.

I built [Bayes of Our Lives](https://github.com/jimador/bayes-of-our-lives) to try it out. It's based on [Tripper](https://github.com/embabel/tripper), which takes a brief, finds places, researches them and proposes a trip. I cut that down to one city and part of one day. Bayes finds sights and places to eat, drafts up to two itineraries, checks the routes and mapped hours and then asks whichever decision service is configured what to do next. Nobody needs a seven-day travel agent to figure out that lunch is missing.

## A Tuesday in Fort Lauderdale

Say your connecting flight is canceled and the next one leaves tomorrow. The airline puts you in a hotel near the airport and now you have a free day in Fort Lauderdale. It could be any city. You'd rather see something than sit in a hotel room, so you want breakfast, something worth seeing and lunch before you head back.

That's the request I gave Bayes, a Tuesday in Fort Lauderdale with two adults, an interest in art, a 7 a.m. to 2 p.m. window, breakfast, a sight and lunch. The agent discovers mapped candidates and uses a language model to select relevant sights. It researches places from venue pages and Wikipedia leads, and then the model proposes alternatives. Bayes resolves each proposed stop back to a mapped place, estimates base drive times with OSRM and checks the schedule and mapped opening hours.

In a live check, Bayes found the NSU Art Museum Fort Lauderdale and Bonnet House Museum and Gardens, along with three mapped cafés and several restaurants. Each request proposed two plans, one built around each art stop. Both places are mapped as open on Tuesday, NSU from 11:00 and Bonnet House from 11:00 to 4:00. No plan was invalid. Every plan was still unresolved: the café records lacked opening hours and full-breakfast evidence, so the planner selected none for 7 a.m. That says something about this search and its evidence, not about whether Fort Lauderdale serves breakfast. Admission and hours on the specific date also remained unverified.

The language model does the open-ended research and drafting here, whatever decides at the end. The decision engine sees the same alternatives with their statuses, timing and open-question counts. A model cannot turn an invalid alternative into a valid one by sounding pleased with it. A closure is a fact in the input, and the status arrives with it.

## What the decision action asks

The itinerary action uses Embabel Agent 1.5.4-SNAPSHOT's `DecisionSpec` for three questions. Is any plan sufficiently sourced? Which non-invalid alternative should we choose, or should we research? How strong is the fit? It uses `ClassificationSpec` to report the selected itinerary type. This excerpt uses the same API calls as `BayesAgent.decideItinerary`:

```java
var spec = DecisionSpec.builder()
    .proposition("fully-sourced", q -> q.asking(
        "Is any alternative marked provisional with no open questions? This is still not a confirmed booking."))
    .choice("next", q -> {
        q.asking("Choose a non-invalid alternative that best fits the confirmed profile and sourced timing."
            + " Choose research if material gaps prevent a useful tentative itinerary."
            + " Never claim an unresolved alternative is confirmed.");
        for (var alternative : alternatives)
            q.option(alternative.id(), alternative.title() + " (" + alternative.status() + ")");
        q.option("research", "Ask or research before choosing an itinerary");
    })
    .rating("fit", q -> q.asking("Rate the best non-invalid alternative's fit and evidence")
        .level("strong", "Provisionally fits with mapped evidence")
        .level("possible", "Tentative with open questions")
        .level("weak", "No usable alternative"))
    .build();
var service = services.forItineraryEngine(profile.requirements().engine());
var answer = service.ask(DecisionRequest.of(input, spec));
```

Each question has a declared answer type. A proposition comes back as true or false. A choice comes back as one of the listed option IDs, and the option list is built from the alternatives the agent actually has, plus `research`. A rating comes back as one of three named levels. The app never has to parse free text to find out what the model meant.

The production method also calls `service.classify(ClassificationRequest.of(...))` with a `ClassificationSpec` for `sight-and-meal`, `sight-only`, and `unknown`. `BayesDecisionServices` maps the request's `engine` to deterministic rules, TypeSafe Jev, or an LLM-backed decision service. That is the configuration change. The same `@Agent` actions discover, research, propose, decide, and return the plan.

On the Embabel side, this split is the design. GOAP plans which actions run and in what order. The `DecisionService` answers small, bounded questions inside an action. Keeping those two jobs apart means you can swap the thing answering the questions without touching the plan, the actions or the domain types. If you want to know whether a decision model earns its place in your agent, you change one setting and run the same inputs again.

If the answer is `research`, Bayes looks for a relevant official-site subpage. When it finds new evidence, it researches the place again, replans and redecides once. Then it stops and shows any questions still open. Endless travel homework would be a poor feature, and one extra lap is enough to show whether new evidence changed the answer.

## Dice supplies a prior

[Dice](https://github.com/embabel/dice) holds confirmed interests and pace. With **Remember preferences** selected, those two preferences can be saved under a profile ID and used on a later outing. Bayes puts the confirmed Dice interests and pace into the decision input alongside the current day, party, access requirement and candidate summaries. Meals and dietary needs come from the current request.

Think of the friend who knows you like slow mornings and museums. That is useful when picking between two plans. A saved interest in art helps assess fit. It says nothing about today's opening hours, admission, step-free access, menu or traffic. Dice gives the agent a starting belief about the person, and the day still has to supply its own evidence.

Tagged opening hours are provisional unless they show a closure. Public sources can leave the other facts unknown, and Bayes keeps those gaps in the returned plan. Food venues need direct confirmation. The exact allergy text stays out of model requests.

## Three engines on one captured question

For a small fixed replay, I captured one live Fort Lauderdale decision input and sent its identical SHA-256 content through `BayesAgent.decideItinerary`. Plan A was the NSU Art Museum with lunch at a Brazilian restaurant. Plan B was Bonnet House with the same lunch. Both were unresolved with five open questions each, and neither was invalid. Each engine got one warmup call and two measured decision calls. Every typed answer was valid.

| Decision engine | Answer | Measured decision calls |
| --- | --- | --- |
| Deterministic rules | Tentative A | 0.371 ms, 0.229 ms |
| TypeSafe `jev-1.13.0` | Research | 144 ms, 217 ms |
| OpenAI `gpt-4.1` | Research | 772 ms, 743 ms |

The remote call times include adapter work, network, provider processing and parsing. I cannot separate model inference from that total. Two measurements do not establish a speed ranking or a quality ranking. This replay also skips discovery, place research, routing and planning. Those steps use separate providers, so the table covers the decision action only. The [replay note](https://github.com/jimador/bayes-of-our-lives/blob/main/results/itinerary-replay-fort-lauderdale-2026-10-13.md) has the method, the capture and the classification times.

The deterministic rules chose a tentative A. Both remote engines asked for research. Three engines gave two different answers, and every answer arrived in the expected type. Nobody said itinerary planning was a solved problem. One small wrinkle showed up too. On its second measured call, the LLM answered yes to "is any plan fully sourced" while still choosing research. The typed answer was valid and the choice was consistent with the evidence, but the two parts of the same answer disagreed with each other.

The disagreement between engines is the interesting part. The rules see unresolved plans with no invalid stops and take one tentatively. Both models looked at the same open questions and decided the day was not ready yet. You could argue for either. What matters for an agent builder is that both outcomes are typed and handled, so the agent has a defined next step either way.

The [full live requests](https://github.com/jimador/bayes-of-our-lives/blob/main/results/live-fort-lauderdale-check-2026-10-13.md) took 27 to 33 seconds each, far longer than these isolated decisions. Each one ran its own research and planning, so the three engines saw different plans and those runs are unsuitable for an engine comparison. They matched the replay anyway. The rules took a tentative plan right away. Jev and the LLM each asked for research, Bayes took its one extra lap, and both still asked for research afterward. The remaining uncertainty stayed in the plan, and the agent stopped there.

## Trying it yourself

The code is in the [Bayes of Our Lives](https://github.com/jimador/bayes-of-our-lives) repository and the README walks through setup. Swap in whatever city you're stuck in. If you already have an Embabel agent with a judgment call buried inside an action, the `DecisionService` interface is a small change to try. Write the question as a `DecisionSpec`, start with deterministic rules or a traditional LLM and switch the configuration to a decision model like Jev when you want to compare. Run the same captured inputs through each and look at both the answers and the timing.

Like sands through the hourglass, so are the API calls of our lives. At least this soap opera knows when to stop researching lunch.

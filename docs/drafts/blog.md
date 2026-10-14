# Bayes of Our Lives: Experimental Decision Models in Embabel Agent

*Like sands through the hourglass, so are the API calls of our lives.*

Get stuck somewhere you never planned to visit and a language model will happily read venue pages and write you an itinerary. Picking between two of those itineraries is a different job. You need to know which one fits what you already know about the day, and when to stop and go find more evidence. TypeSafe built [Jev](https://typesafe.ai/blog/introducing-system-one-models-and-jev) for that kind of question, structured decisions with declared answer types. Decision models are getting enough attention that [Hugging Face added a decision-model tag and filter](https://huggingface.co/changelog/decision-models-on-the-hub) to the Hub.

[Embabel Agent](https://github.com/embabel/embabel-agent) is a JVM framework for building agents from actions and goals. It works with general language models and uses [Goal-Oriented Action Planning](https://docs.embabel.com/) (GOAP) to choose a path through those actions. Embabel Agent recently added support for typed decision and classification models through a `DecisionService` interface. An action can ask a bounded question and get back a typed result. The model behind that interface is configurable, so the same `DecisionService` can be served by a traditional LLM, a decision model like Jev, or deterministic rules. GOAP chooses the agent's actions, and the decision service answers questions inside them. Since switching the backend is a configuration change, it is also a simple way to test the performance claims for models like Jev against a traditional LLM.

I built [Bayes of Our Lives](https://github.com/jimador/bayes-of-our-lives) to try it out. It's based on [Tripper](https://github.com/embabel/tripper), which takes a brief, finds places, researches them and proposes a trip. I cut that down to one city and part of one day. Bayes finds sights and places to eat, drafts up to two itineraries, checks the routes and mapped hours and then asks whichever decision service is configured what to do next. Nobody needs a seven-day travel agent to figure out that lunch is missing.

## A Tuesday in Fort Lauderdale

Say your connecting flight is canceled and the next one leaves tomorrow. The airline puts you in a hotel near the airport and now you have a free day in Fort Lauderdale. It could be any city. You'd rather see something than sit in a hotel room, so you want breakfast, something worth seeing and lunch before you head back.

So I asked Bayes to plan a Tuesday in Fort Lauderdale for two adults who like art, with breakfast, a sight and lunch between 7 a.m. and 2 p.m. The agent discovers mapped candidates and uses a language model to select relevant sights. It researches places from venue pages and Wikipedia leads, and then the model proposes alternatives. Bayes resolves each proposed stop back to a mapped place, estimates base drive times with OSRM and checks the schedule and mapped opening hours.

In the latest live check the request also carried cuisine likes (seafood and Cuban) and a dislike (Italian) as model input. Every plan started with breakfast at OB House at 7:05 a.m. Its mapped tags say `breakfast;coffee;lunch` with weekday hours from 7 a.m. to 1 p.m. The sights were the NSU Art Museum Fort Lauderdale and Bonnet House Museum and Gardens, and no sight was scheduled before its mapped 11:00 opening. Lunch was seafood at Kelly's Landing New England Seafood or Southport Raw Bar & Restaurant, both mapped with seafood tags. Nothing Italian was chosen. Bayes also found Cuban Guys, which is mapped as Cuban, but none of the plans picked it. No plan was invalid. Every plan was still unresolved with five open questions, covering things like full breakfast service at OB House, lunch hours and admission on the date.

Getting there took a few changes. For a breakfast request, Bayes looks for named breakfast restaurants and cafés near the starting point and looks up the exact OSM records for cuisine and hours. For lunch it sends up to two liked cuisines to the place search, prefers a mapped liked cuisine when it picks a restaurant and swaps out a disliked one when there's another option. Dislikes and allergy text stay out of the search. Bayes also fits each stop into the earliest slot the mapped hours allow, which is why the museums now wait for 11:00. OB House is a real candidate with attributed clues, and Bayes still asks for direct confirmation that the kitchen will be serving breakfast next Tuesday.

The language model does the open-ended research and drafting here, whatever decides at the end. The decision engine sees the same alternatives with their statuses, timing and open-question counts. A model cannot turn an invalid alternative into a valid one by sounding pleased with it. A closure is a fact in the input, and the status arrives with it.

## What the decision action asks

The itinerary action uses Embabel Agent 1.5.4-SNAPSHOT's `DecisionSpec` for three questions. Is any plan sufficiently sourced? Which non-invalid alternative should we choose, or should we research? How strong is the fit? It uses `ClassificationSpec` to report the selected itinerary type. Cuisine fit and meal timing go into the decision input as counts of liked, disliked and supported breakfast stops plus the evidence for each stop. The questions themselves didn't change. `BayesAgent.decideItinerary` hands the captured input to `DecisionReplay.evaluate`, so live requests and replays ask exactly the same thing. This excerpt is from that method:

```java
var spec = DecisionSpec.builder()
    .proposition("fully-sourced", q -> q.asking(
        "Is any alternative marked provisional with no open questions? This is still not a confirmed booking."))
    .choice("next", q -> {
        q.asking("Choose a non-invalid alternative that best fits the confirmed profile and sourced timing."
            + " Choose research if material gaps prevent a useful tentative itinerary."
            + " Never claim an unresolved alternative is confirmed.");
        for (var alternative : capture.choices())
            q.option(alternative.id(), alternative.title() + " (" + alternative.status() + ")");
        q.option("research", "Ask or research before choosing an itinerary");
    })
    .rating("fit", q -> q.asking("Rate the best non-invalid alternative's fit and evidence")
        .level("strong", "Provisionally fits with mapped evidence")
        .level("possible", "Tentative with open questions")
        .level("weak", "No usable alternative"))
    .build();
var service = services.forItineraryEngine(profile.requirements().engine());
var answer = service.ask(DecisionRequest.of(capture.input(), spec));
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

For a small fixed replay, I captured the decision input from the live rules request and ran its identical SHA-256 content through the same decision code with the replay runner. Plan A was breakfast at OB House, the NSU Art Museum and a seafood lunch at Kelly's Landing. Plan B was OB House, Bonnet House and lunch at Southport Raw Bar. Both were unresolved with five open questions each, and neither was invalid. Each engine got two measured decision calls with no warmup. Every typed answer was valid.

| Decision engine | Answer | Measured decision calls |
| --- | --- | --- |
| Deterministic rules | Tentative A | 5.355 ms, 0.712 ms |
| TypeSafe `jev-1.13.0` | Research | 397.7 ms, 187.7 ms |
| LLM `gpt-4.1` | Research | 1064.6 ms, 665.3 ms |

The remote call times include adapter work, network, provider processing and parsing. I cannot separate model inference from that total. Two measurements do not establish a speed ranking or a quality ranking. This replay also skips discovery, place research, routing and planning. Those steps use separate providers, so the table covers the decision action only. The [replay note](https://github.com/jimador/bayes-of-our-lives/blob/main/results/itinerary-replay-fort-lauderdale-2026-10-13-v5.md) has the input hash and the classification times.

The deterministic rules chose a tentative A. Both remote engines asked for research. Three engines gave two different answers, and every answer arrived in the expected type. Nobody said itinerary planning was a solved problem. One small wrinkle showed up too. In its own live run the LLM picked plan A right away. On the shared replay input it asked for research like Jev. Each live run did its own research and planning, so the LLM was looking at a different input in that run.

The disagreement between engines is the interesting part. The rules see unresolved plans with no invalid stops and take one tentatively. Both models looked at the same open questions and decided the day was not ready yet. You could argue for either. What matters for an agent builder is that both outcomes are typed and handled, so the agent has a defined next step either way.

The [full live requests](https://github.com/jimador/bayes-of-our-lives/blob/main/results/live-fort-lauderdale-check-2026-10-13-v5.md) took 27 to 35 seconds each, far longer than these isolated decisions. Each one ran its own research and planning, so the three engines saw different plans and those runs are unsuitable for an engine comparison. The rules took a tentative plan right away, and so did the LLM. Jev asked for research, Bayes took its one extra lap, and Jev still asked for research afterward. The remaining uncertainty stayed in the plan, and the agent stopped there.

## Trying it yourself

The code is in the [Bayes of Our Lives](https://github.com/jimador/bayes-of-our-lives) repository and the README walks through setup. Swap in whatever city you're stuck in. If you already have an Embabel agent with a judgment call buried inside an action, the `DecisionService` interface is a small change to try. Write the question as a `DecisionSpec`, start with deterministic rules or a traditional LLM and switch the configuration to a decision model like Jev when you want to compare. Run the same captured inputs through each and look at both the answers and the timing.

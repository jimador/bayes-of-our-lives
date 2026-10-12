# Jev and Embabel — Bayes of Our Lives

Suppose we have a day in Miami. The museum is the easy part. The less glamorous questions arrive at seven in the morning: where are we starting, are we getting breakfast, who is coming, and will lunch happen before someone decides the museum gift shop counts as food?

A map finds places. It does not put a day together for this group. **Bayes**, our small city outing planner, gives that job to [Embabel Agent](https://github.com/embabel/embabel-agent). The agent gathers requirements, finds candidates, does a bounded look at venue websites, builds timed alternatives, and asks a decision engine a typed question. Swap the engine among local Java rules, TypeSafe Jev, OpenAI `gpt-4.1`, and an optional local CLEF model; the agent's itinerary actions and types stay put.

[Tripper](https://github.com/embabel/tripper) supplied the journey's shape: brief, ideas, researched points of interest, proposal, review, plan. Bayes adapts that shape for a single city outing. The request has a date and window, origin, food choices, party size, interests and pace, plus optional child, access, or dietary needs. Leave breakfast and coffee blank for a 7am start, and the first answer is a question. The browser lets you answer it and rerun the outing. No map or model call is needed to notice that someone might want breakfast.

## A little memory, with a boundary

Bayes records confirmed interests and pace with [Dice 0.4.0](https://github.com/embabel/dice). With your **Remember preferences** choice, Dice's JSON proposition repository saves those two fields under a browser-generated profile ID. A later outing can opt to **Use remembered preferences**. We tested a save, a reload with another service instance, and recall on a later outing. Without that choice, the memory is just for the request. Today's meals, party, access, and dietary needs still come from today's form. Exact allergy text stays out of map searches and model input; it triggers a direct venue-check question instead.

This is a preference, not a permission slip. Remembering “art” does not make a museum open on Tuesday.

## Research does not mean certainty

Open-Meteo geocodes the city. Nominatim finds mapped sights and the origin; Photon supplies mapped cafés and restaurants; OSRM supplies base driving estimates. The app keeps OpenStreetMap links and tags with each candidate. For up to six mapped candidates, it also tries the HTTPS website recorded in OSM and shows short page clues with retrieval status. Some sites refuse access; many places have no website tag. Page text is a lead for a human to check, not verified admission, hours, or menu facts.

The planner makes two breakfast–sight–lunch alternatives when enough candidates exist. It gives stops illustrative dwell times, checks meal windows and the finish time, and records gaps. A mapped closure invalidates an option. A `wheelchair=no` tag invalidates an option that needs step-free access. Missing admission, cuisine, child suitability, accessible route detail, or food safety becomes a question. Even a mapped “open” or “wheelchair=yes” remains provisional. OSRM has no live traffic; Bayes asks about traffic only when the schedule is tight. The point is to show a plan with its weak spots, not to launder missing facts into confidence.

The [reuse map](../tripper-reuse-map.md) names the Tripper stages Bayes adapts and the tools it does not carry over. Tripper's broad web, browser, weather, image, and lodging research is not running here. Bayes has one narrow independent website retrieval action. If a decision requests more research, Bayes reports the remaining questions; it does not automatically search again. You can revise the form and resubmit.

## One decision boundary, four implementations

`BayesAgent.decideItinerary` constructs Embabel's `DecisionSpec`: a proposition about whether a provisional option has no open questions, a choice among A, B, and `research`, and a fit rating. It also sends a separate `ClassificationSpec` for the selected itinerary shape. The decision input includes accepted Dice preferences, today's requirements, alternative statuses, timings, and the first outstanding gaps. The Java planner applies hard invalidity before the result is shown. A model cannot talk a closed museum into being open.

```java
var service = services.forItineraryEngine(profile.requirements().engine());
var answer = service.ask(DecisionRequest.of(input, builder));
var classification = service.classify(
    ClassificationRequest.of(input + "\nSelected itinerary type: " + type, shape));
```

That service selection is the small switch. Local rules implement Embabel's typed decision service; Jev and OpenAI are remote implementations; CLEF uses a separate prepared local model and a bounded bridge. No CLEF weights are shipped in the example. The [repo](https://github.com/jimador/bayes-of-our-lives) contains the runnable app, capture, replay harness, tests, and setup.

## What one Miami day actually said

On October 12, 2026, we ran a synthetic, nonpersonal live outing for Miami: Tuesday October 13, 7am–2pm, breakfast, an art sight, lunch, two adults, steady pace. Bayes built two timed alternatives. Pérez Art Museum Miami's mapped Tuesday hours invalidated A. B used Cafe Con Leche, Patricia & Phillip Frost Art Museum, and El Cartel Colombian Restaurant, but current hours and museum admission were still unknown. Local rules tentatively picked B; Jev and `gpt-4.1` asked for research. No engine confirmed the day. Those three whole-outing requests had changing public lookup results and different decision-input hashes, so their [one-run observations](../../results/live-itinerary-check-2026-10-12.md) are a smoke check, not a controlled timing race.

To compare the same decision, we captured a separate synthetic Wednesday outing after discovery and proposal. Every engine received the same decision input, SHA-256 `e835eaa08dd0c0c8eca1d5c3bc2ed71a368a1453fd8a600d8daaf7fa213db46d`. A was invalid; B was unresolved. After one warmup, we measured two decision/classification calls each for local rules, Jev, and OpenAI; CLEF had one measured call and no warmup. All typed responses were valid. Local rules and CLEF chose tentative B. Jev and OpenAI chose `research`. Both answers are defensible when facts are missing.

Measured **decision-call** times were 0.264 and 0.193 ms locally, 194 and 171 ms for Jev, 1132 and 982 ms for `gpt-4.1`, and 2268 ms for local CLEF's single run. The classification calls and exact counts are in the [replay notes](../../results/itinerary-replay-2026-10-12.md). Remote durations include adapter, network, provider work, and parsing; we cannot isolate their model inference. CLEF's bridge reported about 1925 ms of model inference for its decision, inside about 2224 ms of bridge wall time. Replay bypasses map lookup and much of the agent path. A tiny sample with different environments is not a speed ranking, much less a quality ranking.

The useful result is a visible seam. Embabel Agent does the itinerary work; Dice can carry one confirmed preference into a later outing; the typed decision can pick a provisional plan or ask for homework. The app keeps the unanswered questions beside the plan. Seven o'clock breakfast deserves at least that much honesty.

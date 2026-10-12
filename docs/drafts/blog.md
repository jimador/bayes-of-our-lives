# Jev and Embabel — Bayes of Our Lives

*Editorial note: This draft describes the tested local Tripper port. The public repository still has the earlier build; publish this article only after the port is reviewed and released.*

Suppose we have one day in Miami. The museum is the easy part. The less glamorous questions arrive at seven in the morning: where are we starting, are we getting breakfast, who is coming, and will lunch happen before someone decides the gift shop counts as food?

I wanted an agent to do the work a reasonable travel companion would do: find candidates, research them, put together a day, and explain what it still does not know. I also wanted to change *how the final choice is made* without rewriting that work. Bayes uses [Embabel Agent](https://github.com/embabel/embabel-agent) for the sequence and its typed decision API for the choice. Local Java rules, TypeSafe Jev, OpenAI `gpt-4.1`, and an optional local CLEF adapter answer the same itinerary-level questions.

## Tripper, narrowed to one day

[Tripper](https://github.com/embabel/tripper) provided the action contracts: brief → ideas → researched places → proposed plan. Bayes ports those stages to Java for a city outing, then adds a decision on the **whole itinerary**. We did not replace Tripper's research and planner with map tags and a fixed schedule. An OpenAI researcher first chooses relevant sights from mapped candidates. Separate LLM calls research up to four places using retrieved venue website and Wikipedia leads. An OpenAI planner then proposes venue choices and stop order for up to two alternatives. Java maps those names back to known candidates, computes routes and times, and rejects hard conflicts. The LLM writes the proposal; code checks whether the day can actually fit.

The live app needs an existing runtime `OPENAI_API_KEY` for those research and planning calls, regardless of the selected *decision* engine. It asks for explicit consent before sharing non-sensitive outing details. Choosing Jev for the decision additionally uses the runtime `TYPESAFE_API_KEY`. This is a small switch at the decision boundary, not a claim that an entire trip can be made without a model. The fixed-input decision replay can still run local rules without a model key.

There is a tooling detail worth saying plainly. Embabel 1.5.4-SNAPSHOT defines web, maps and browser tool-group roles, as Tripper uses, but this app's runtime had no MCP providers registered for them. I checked the actual platform registry; it contained math and AppleScript. The Java port uses bounded lower-level public providers instead: Open-Meteo, Nominatim, Photon, OSRM, HTTPS venue pages, and Wikipedia search leads. Those provide real discovery and independent page evidence without pretending the missing MCP groups are there. Brave images, weather, lodging and Google login are not part of this one-day guide. [The source map](../tripper-reuse-map.md) records what was ported and what was not.

## Memory is not a hall pass

Bayes records confirmed interests and pace in [Dice 0.4.0](https://github.com/embabel/dice). Choose **Remember preferences** and Dice's JSON repository saves those two fields under a browser-generated profile ID; a later outing can opt to use them. Without that choice, capture lasts for the request. Today's meal, party, access and dietary needs still come from today's form. Exact allergy text stays out of map searches and model prompts; Bayes asks for direct confirmation at a food stop instead.

Remembering “art” does not make a museum open on Tuesday.

## A proposal with the awkward bits attached

A retrieved homepage may be blocked, and a Wikipedia hit may not even be the right venue. Bayes keeps the source and labels clues as unverified. The planner cannot select an invented restaurant; names must match mapped candidates. A mapped closure invalidates an alternative. Tagged “open” hours remain provisional. Missing admission, menu, child suitability, step-free route detail, and food safety stay as questions. OSRM gives base driving time, not live traffic. If the time window is tight, Bayes asks for a traffic check.

The decision action gives each engine an Embabel `DecisionSpec`: is any alternative provisional with no open questions; choose A, B, or `research`; rate the fit. A separate `ClassificationSpec` reports the chosen itinerary shape. The agent still supplies the same Dice context, researched candidates, plan, timing, and open questions when we change engines. Code prevents an invalid option from becoming the presented plan even if a model chooses it.

If the decision says `research`, Bayes takes **one** targeted pass at a relevant official-site subpage, if one exists. It sends new evidence to the LLM researcher, asks the planner to revise, then makes one more typed decision. If no new page is found, or the second decision still asks for research, the app stops and reports the gaps. Endless travel homework would be a poor feature.

## What the measured runs say

We used a synthetic Miami outing with breakfast, an art sight and lunch. One live call with local Java decisions completed in 17.4 seconds and tentatively chose unresolved B. One live JEV call completed in 20.0 seconds, requested research, made one targeted research/replan/redecision pass, and still returned `needs_research`. Both responses contained two timed alternatives and eight mapped research records. These are [two smoke observations](../../results/tripper-port-smoke-2026-10-12.md), not a race: they include changing map pages, Wikipedia, multiple OpenAI calls, network, and agent work. We cannot isolate model inference from those whole-request times.

For a cleaner decision comparison, we replayed a separate captured itinerary through the same `BayesAgent.decideItinerary` boundary with an identical SHA-256 input for all four engines. A was invalid; B was unresolved. Local rules and CLEF picked provisional B; Jev and OpenAI requested research. All typed outputs were valid. After one warmup, the two measured **decision-call** times were 0.264 and 0.193 ms locally, 194 and 171 ms for Jev, and 1132 and 982 ms for `gpt-4.1`. CLEF had one measured run, 2268 ms, with no warmup. [The replay note](../../results/itinerary-replay-2026-10-12.md) gives classification times and limits. Remote calls include adapter, network, provider processing and parsing; CLEF used a separately prepared local CPU model. This small sample does not establish a speed or quality ranking, and replay deliberately skips discovery, research, and planning.

The useful thing is the seam. Embabel Agent can carry a real, sourced outing through research and proposal; Dice can recall a narrow preference; and a typed decision can pick a tentative plan or ask for more work. The source links and unanswered questions travel with the plan. Seven o'clock breakfast deserves at least that much honesty.

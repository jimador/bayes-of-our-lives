# Bayes of Our Lives: Jev and Embabel

Suppose I have one day in Miami. The museum is the easy part. The less glamorous questions arrive at seven in the morning: where am I starting, are we getting breakfast, who is coming, and will lunch happen before someone decides the gift shop counts as food?

I wanted an agent to do the work a reasonable travel companion would do: find candidates, research them, put together a day, and explain what it still does not know. I also wanted to change *how the final choice is made* without rewriting any of that work.

That second part is the real point of this post. [Embabel Agent](https://github.com/embabel/embabel-agent) now has typed decision and classification models, `DecisionSpec` and `ClassificationSpec`, and it still supports the generic agents that do the rest of the job. Bayes is a small demo built on both. Embabel runs the sequence, and the typed decision API makes the final call. I can swap who makes that call between local Java rules, Jev (TypeSafe's model, reached with its own runtime key), and OpenAI `gpt-4.1`, and research and planning never notice.

## How the day gets built

I started from [Tripper](https://github.com/embabel/tripper), which provided the action contracts: a brief, then ideas, then researched places, then a proposed plan. Bayes ports those stages to Java for a city outing and adds a decision on the **whole itinerary**.

I was tempted to replace Tripper's research and planner with map tags and a fixed schedule. I didn't. An OpenAI researcher first chooses relevant sights from mapped candidates. Separate LLM calls research up to four places using retrieved venue website and Wikipedia leads. An OpenAI planner then proposes venue choices and stop order for up to two alternatives. Java maps those names back to known candidates, computes routes and times, and rejects hard conflicts.

The LLM writes the proposal; code checks whether the day can actually fit.

## The decision seam

This is the part I cared about most. The decision action gives each engine an Embabel `DecisionSpec`: is any alternative provisional with no open questions; choose A, B, or `research`; rate the fit. A separate `ClassificationSpec` reports the chosen itinerary shape. When I change engines, the agent still supplies the same Dice context, researched candidates, plan, timing, and open questions. Only the judge changes; the evidence stays put.

Models are allowed to have opinions, but not the last word on validity. Code prevents an invalid option from becoming the presented plan even if a model chooses it.

If the decision says `research`, Bayes does one more lap, then shows its work. It takes **one** targeted pass at a relevant official-site subpage, if one exists. It sends new evidence to the LLM researcher, asks the planner to revise, then makes one more typed decision. If no new page is found, or the second decision still asks for research, the app stops and reports the gaps. Endless travel homework would be a poor feature.

## Memory is not a hall pass

Bayes records confirmed interests and pace in [Dice 0.4.0](https://github.com/embabel/dice). Choose **Remember preferences** and Dice's JSON repository saves those two fields under a browser-generated profile ID; a later outing can opt to use them. Without that choice, capture lasts for the request. Today's meal, party, access and dietary needs still come from today's form. Exact allergy text stays out of map searches and model prompts; Bayes asks for direct confirmation at a food stop instead.

Dice gives the agent a prior, not a prophecy. Remembering "art" does not make a museum open on Tuesday.

## A proposal with the awkward bits attached

The web is not always cooperative. A retrieved homepage may be blocked, and a Wikipedia hit may not even be the right venue. Bayes keeps the source and labels clues as unverified. The page fetch pins a checked public IP, verifies the HTTPS hostname, and refuses redirects. The planner cannot select an invented restaurant; names must match mapped candidates. A mapped closure invalidates an alternative.

Here is what Bayes won't pretend to know:

- **Hours:** tagged "open" hours remain provisional.
- **The details:** missing admission, menu, child suitability, step-free route detail, and food safety stay as questions.
- **Traffic:** OSRM gives base driving time, not live traffic. If the time window is tight, Bayes asks for a traffic check.
- **Clocks:** the date and times use the destination's geocoded timezone; a daylight-saving crossing stays an open question.

## What the measured runs say

For a clean decision comparison, I replayed a separate captured itinerary through the same `BayesAgent.decideItinerary` boundary with an identical SHA-256 input for every engine. A was invalid; B was unresolved. All typed outputs were valid, even where the engines agreed to disagree in valid JSON. After one warmup, the two measured **decision-call** times were:

| Engine | Choice | Measured decision-call times |
| --- | --- | --- |
| Local rules | tentative B | 0.264 ms, 0.193 ms |
| Jev | research | 194 ms, 171 ms |
| OpenAI `gpt-4.1` | research | 1132 ms, 982 ms |

Remote calls include adapter, network, provider processing and parsing. This small sample does not establish a speed or quality ranking, and replay deliberately skips discovery, research, and planning. [The replay note](../../results/itinerary-replay-2026-10-12.md) gives classification times and limits.

There are other decision models on the market as their popularity grows, and I also wired up an experimental local CLEF adapter. On the same replay, CLEF picked tentative B in one measured run of 2268 ms, with no warmup, on a separately prepared local CPU model. The CLEF Python bridge in this repo is an experimental legacy adapter pending a separate provider redesign.

I also ran the full app on a synthetic Miami outing with breakfast, an art sight and lunch. In the initial port, one local-decision call completed in 17.4 seconds and chose unresolved B; one Jev call completed in 20.0 seconds, used the single research retry, and still asked for more research. During the security audit, one request failed after 56.9 seconds; a subsequent hardened local run completed in 21.2 seconds with two alternatives, four LLM research summaries and a tentatively selected unresolved A. The [run notes](../../results/tripper-port-smoke-2026-10-12.md) retain the failure and build differences. These smoke runs are a stroll, not a race: they include changing map pages, Wikipedia, multiple OpenAI calls, network, and agent work. I cannot isolate model inference from those whole-request times.

## What's real and what's not

The fine print, stated plainly:

- **OpenAI is always involved.** The live app needs an existing runtime `OPENAI_API_KEY` for the research and planning calls, regardless of the selected *decision* engine. This is a small switch at the decision boundary, not a claim that an entire trip can be made without a model. The fixed-input decision replay can still run local rules without a model key.
- **Jev has its own key.** Choosing Jev for the decision additionally uses the runtime `TYPESAFE_API_KEY`.
- **Consent comes first.** The app asks for explicit consent to share the city, date, group, interests, pace and access requirement. The exact starting location and allergy text stay out of model prompts.
- **The tools are not all there.** Embabel 1.5.4-SNAPSHOT defines web, maps and browser tool-group roles, as Tripper uses, but this app's runtime had no MCP providers registered for them. I checked the actual platform registry; it contained math and AppleScript. The Java port uses bounded lower-level public providers instead: Open-Meteo, Nominatim, Photon, OSRM, HTTPS venue pages, and Wikipedia search leads. Those provide real discovery and independent page evidence without pretending the missing MCP groups are there.
- **Some things are out of scope.** Brave images, weather, lodging and Google login are not part of this one-day guide. [The source map](../tripper-reuse-map.md) records what was ported and what was not.

## The seam is the story

What I got out of this is the seam. Embabel Agent can carry a real, sourced outing through research and proposal; Dice can recall a narrow preference; and a typed decision can pick a tentative plan or ask for more work, no matter who is making the call. The source links and unanswered questions travel with the plan. Like sand through the hourglass, so are the days of our lives, and I'd rather not spend one of them guessing. Seven o'clock breakfast deserves at least that much honesty.

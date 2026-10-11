# JEV and Embabel Agent Make Some Decisions

TypeSafe calls [Jev a System One model](https://typesafe.ai/blog/introducing-system-one-models-and-jev). Give it the facts of a situation and a bounded question, and it returns a typed answer. We added decision and classification requests to Embabel Agent so those answers can guide an agent as it gathers evidence and works toward a goal. Result quality and execution times may vary.

Suppose you have one free afternoon in a city you don't know. [Tripper](https://github.com/embabel/tripper) can find places, research them, and work toward a travel plan. The interesting part comes when those researched places have to become *your* afternoon. A place may fit the dates but lack a useful source. Another may be farther away than the forecast and your schedule allow. The agent needs to settle those questions before it decides what belongs in the plan.

We added that review step to Tripper. After it researches points of interest, `reviewPointsOfInterest` asks the decision service about each one and passes eligible places to a separate ranker. Local Kotlin rules, Jev, and OpenAI all use the same request contract. Changing `tripper.decision.engine` changes who answers the questions; it does not require another set of planning actions. Tripper's existing full-journey flow remains in place.

## An afternoon the agent can actually work through

To see the whole decision path with real place and forecast data, we added a smaller city-outing agent inside Tripper. Give it a geocodable city and it looks for mapped museums, gets hourly weather, and estimates a base drive. Embabel carries the outing from discovery and assessment through ranking to a final research lead. The information and model answers remain visible along the way.

In Miami, the agent found [P&#233;rez Art Museum Miami](https://www.openstreetmap.org/way/484587327) and two Frost museums. It brought together a forecast and drive estimate for each, then worked out which places merited a closer look. The traffic delay in this example was simulated.

Each request answers a different developer question. Use a **proposition** for a yes-or-no check, such as whether a lead has enough weather and travel information to assess. Use a **choice** when the answer must be one of a few actions: `candidate`, `review`, or `defer`. Use a **rating** to describe one lead's planning effort as `easy`, `unknown`, or `hard`. Use **classification** to put a drive in a named category, here `nearby` or `long-drive`. Then use the application-owned Embabel **ranker** to order the eligible leads. A rating describes one place; ranking compares places. The application decides which typed answers qualify a lead for ranking, so a provider's uncertain or failed response does not quietly become approval.

The Miami comparison shows why the boundary matters. Local rules and Jev qualified all three museums. OpenAI `gpt-4.1-mini` returned typed `INVALID_RESPONSE` outcomes for choice and rating on two, so the application excluded them. All three backends put P&#233;rez Art Museum Miami first, but they did not agree on what belonged in the candidate set. A developer can swap the decision engine and see both the answers and their effect on the plan, without rebuilding the agent's planning steps.

The goal card calls it a **research lead**. OpenStreetMap supplied mapped places; Open-Meteo and OSRM supplied forecasts and base-drive estimates. The run did not verify museum hours, admission, accessibility, or live traffic. Its traffic delay and policy thresholds were fictional. The card gives you a place to investigate next and shows the trip details still missing.

## The same questions in a larger trip

The outing is a compact way to see a capability added to Tripper's original journey planner. There, the proposition asks whether a researched place has a location and source and fits the travel dates. The choice is `prioritize`, `review`, or `skip`; the rating captures planning effort; classification names an indoor, outdoor, or unknown setting. Decision answers determine which places reach the local ranker. Words in the journey brief and traveler descriptions then influence their order before Tripper drafts its existing `TravelPlan`. The questions stay bounded even when the trip is larger.

Traveler interests can also come from an optional [Dice](https://github.com/embabel/dice) graph preference. With Dice enabled, Spring Boot starts an app-owned Neo4j container, stores and reads a fictional `art` preference, and applies a bounded ranking bonus. A separate Miami outing retrieved that preference and stopped the container on normal exit. The default outing needs no graph service; the preference is made up for the demo.

Each backend completed the five-action Miami outing over three mapped leads. A local Lisbon run found three museums and put Museu Calouste Gulbenkian first. Nine offline tests and packaging passed. Each Miami backend ran once without warmup, so its elapsed time is an observation, not a speed benchmark. The original full-journey planner still needs its broader travel and login integrations before that separate path can run end to end.

That separation is useful beyond museums. Embabel plans the work, a selected backend answers bounded questions, and application rules decide what those answers mean for the goal. You can try another decision engine while keeping the same agent flow and still see exactly why a candidate became the research lead.

Result quality and execution times may vary. Consult your app configuration for requests lasting longer than one minute. For best results use the right model for the right job. Embabel Agent the right tool for your agentic APIs.

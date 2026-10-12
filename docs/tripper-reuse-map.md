# Tripper reuse and Bayes implementation map

Reference: Embabel Tripper base commit `002a08c`, preserved in this repository at `e56b17b:examples/tripper-decision-demo/`. The original Kotlin files retain Apache-2.0 notices in history; [NOTICE](../NOTICE.md) and the retained [license](../licenses/TRIPPER-APACHE-2.0.txt) record provenance. This map distinguishes ports of the flow from features that still need implementation.

| Upstream file and contract | Bayes code | Current fidelity |
| --- | --- | --- |
| `agent/domain.kt`: `JourneyTravelBrief`, `Travelers`, `PointOfInterest`, `ItineraryIdeas`, `ResearchedPointOfInterest`, `PointOfInterestFindings`, `ProposedTravelPlan`, `TravelPlan` | `ItineraryRequest`, `BayesAgent` records, `ItineraryPlanner` records | Java types represent one city outing, two timed alternatives, source URLs and gaps. Traveler narrative, multi-day route, lodging, images, budget, weather and HTML are absent. |
| `agent/TripperAgent.kt`: `confirmExpensiveOperation` | `ItineraryRequest` model/map consent and bounded category searches | Explicit consent exists; original cost confirmation and budgeted tool run are not ported. |
| `agent/TripperAgent.kt`: `findPointsOfInterest` with `CoreToolGroups.WEB`, `MAPS`, `MATH`, weather | `BayesAgent.findPointsOfInterest` → `ItineraryResearch` → Open-Meteo, Nominatim, Photon, OSRM | Real public map discovery and base routes; no Embabel tool-group use, broad web search, weather or event research. |
| `agent/TripperAgent.kt`: `researchPointsOfInterest` with web, browser automation, weather, `BraveImageSearchService`, parallel research | `BayesAgent.researchPointsOfInterest` and `OfficialPageResearch` | Bounded independent retrieval of HTTPS websites recorded on OSM places, with source and retrieval status. This does not reproduce Tripper's broad research tools or establish current facts from page clues. |
| `agent/TripperAgent.kt` and `agent/PointOfInterestDecisionReview.kt`: review researched POIs through typed decision/classification, rank accepted research | `BayesAgent.decideItinerary`, `LocalItineraryDecisions`, Jev, OpenAI, CLEF adapter | Same Embabel `DecisionSpec`/`ClassificationSpec` API; review is after whole-itinerary proposal instead of upstream POI-before-plan review. CLEF can be selected with a separately prepared local runtime. |
| `agent/TripperAgent.kt`: LLM `proposeTravelPlan` with web/maps/math, sourced prose and HTML | `ItineraryPlanner` deterministic schedule | Timed sourced stops and hard constraints are present; no LLM plan writer or independent fact verification. |
| `agent/TripperAgent.kt`: `findPlacesToSleep` with `ToolsConfig.AIRBNB`; `postProcessHtml` | None | Intentionally outside single-day outing scope. |
| `config/ToolsConfig.kt`: `McpToolGroup` for Airbnb | None | No Airbnb MCP client configured. |
| `Brave.kt`: Brave search | None | `BRAVE_API_KEY` is absent in current runtime; no Brave adapter in Bayes yet. |

Current public map endpoints need no key. Jev and OpenAI use runtime `TYPESAFE_API_KEY` and `OPENAI_API_KEY`; values are not stored in the repo. `BRAVE_API_KEY` and Google OAuth client variables were absent during verification. Weather, browser, and Airbnb tool groups from Tripper are not registered in the Bayes app. Optional local CLEF model and checker assets are external to this repo and must stay out of Git.

Dice 0.4.0's JSON file proposition repository now supports opt-in, profile-scoped preference recall across runs. The UI can answer an early food question and resubmit revised requirements. It does not yet offer a server-side pause/resume graph or automatic research after a `research` decision. This is a narrower runnable adaptation of Tripper, not its full tool or plan-writing implementation.

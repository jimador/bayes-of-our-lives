# Tripper-style Java port smoke check — 2026-10-12

This check used one synthetic Miami city outing from the committed capture's request fields. It contains no personal profile. The new Embabel actions ran LLM discovery over mapped sight candidates, per-place LLM research over independently fetched venue website and Wikipedia leads, LLM plan proposals, Java schedule/hard-constraint checks, then the interchangeable typed itinerary decision. The keyless public lookups are a lower-level adaptation of Tripper's tool capabilities: Embabel's own `WEB`, `MAPS`, and `BROWSER_AUTOMATION` groups were **not** registered in this runtime. A `bays.inspect-tools=true` startup reported only `math` and `AppleScript` groups. No Brave, weather, browser MCP, or Airbnb dependency was used.

| Decision engine | Runs | Whole API wall | Final status / choice | Targeted research retry |
| --- | ---: | ---: | --- | ---: |
| Local Java rules, initial port | 1 | 17.4 s | unresolved / B | 0 |
| TypeSafe `jev-1.13.0`, initial port | 1 | 20.0 s | needs_research / research | 1 |
| Local Java rules, audit build | 1 | 56.9 s | HTTP 502 / no choice | unknown |
| Local Java rules, hardened audit build | 1 | 21.2 s | unresolved / A | 0 |

The first two responses were HTTP 200 with two timed alternatives and eight mapped research records. The initial JEV retry retrieved a distinct official-site follow-up page, called the LLM researcher and planner again, and made a second typed decision. It stopped after that one retry even though the final decision still requested research. The first local run made no retry because it chose B. Current venue suitability and availability remained unresolved.

During the audit, one local request failed with HTTP 502 after 56.9 seconds. Its precise failing stage was not captured. We tightened source handling and added safe stage diagnostics, then ran one more local request: HTTP 200 in 21.2 seconds, two alternatives, four LLM research summaries, and tentative unresolved A. Of four attempted venue pages, one was retrieved, one unavailable, and two had no mapped website; four other mapped records were not requested. This is a different build and changing public evidence, not a controlled before/after benchmark. The final retry-targeting change is covered by a deterministic test, not a new live JEV claim.

Both runs used the existing runtime OpenAI key for discovery, per-place research (`gpt-4.1-mini`), and plan drafting (`gpt-4.1`); JEV additionally used the existing TypeSafe key for its decision. Key values were never printed or saved. The synthetic form has explicit remote research/planning consent in the current build. The local Java choice refers only to the **decision** implementation; its live research and planning still call OpenAI. The action flow may make up to one discovery call, four initial place-research calls, one plan call, and, only after `research` with new official-page evidence, one further research call and one further plan call. It makes one decision/classification pair, or two after a retry. The run is bounded by four initial researched candidates and one retry; public lookup timeouts are separately bounded.

The wall times include public map lookups, website and Wikipedia calls, OpenAI client/network/provider work, Java scheduling, and Embabel agent overhead. They do not isolate OpenAI inference or provide a speed comparison between decision engines. Provider results and page availability can change. The [fixed-context replay](itinerary-replay-2026-10-12.md) separately compares decision calls on byte-identical captured input; it bypasses these LLM research and planning stages.

The live smoke records document the builds used at the time of each run. Repeating them can yield different candidates, evidence, and timings. No new remote run was made solely to claim a performance improvement from the final hardening changes.

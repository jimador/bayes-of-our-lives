# Fort Lauderdale integration observation — 2026-10-14

Functional acceptance is **blocked** on the initial build: local and Jev returned no usable outline; OpenAI selected a usable Cuban lunch outline but its seafood alternative was invalid. Passing model-free tests does not establish completion of the real-city flow.

## Setup and count

Source: signed UI/docs commit `83be95a`, backend predecessor `0101de5`. Packaged `target/bayes-0.1.0.jar`, owned local port 18085 (18084 occupied), startup 3.302 seconds. Existing inherited runtime keys were used; neither was printed or copied. Discovery/research `gpt-4.1-mini`, planner `gpt-4.1`, same across all engines. Decisions independently selected local Java rules, named Jev or named OpenAI service.

Three actual browser form submissions, one per engine, in local → Jev → OpenAI order. Zero warmups, zero provider reruns. Separately: one actual early-choice needs-input request, two complete invalid-engine validation probes (the first probe had a client logging typo after receiving its response), one past-date validation probe and one sparse-body error probe. These terminal validation requests made no provider/model calls. Cancel/stale-response and browser error rendering were separately labeled no-cost fixtures.

Fixed synthetic outing: Fort Lauderdale, Florida; Downtown Fort Lauderdale; Tuesday 2026-10-20; destination-local 07:00–14:00; two adults, no children; art, steady pace; breakfast and lunch; seafood/Cuban likes, Italian dislike; empty private dietary field; public-map and remote-model consents. No saved-preference opt-in. Destination timezone returned `America/New_York` for all live requests.

## Initial measured outputs

| Selected engine / actual decision model | HTTP | Browser wall ms | Backend ms | Coverage / choice / confidence / classification | Result |
| --- | --- | ---: | ---: | --- | --- |
| local / `local/bayes-itinerary-rules-v2` | 200 | 34047 | 32587 | false / research / weak / no-valid-outline | needs_research; both alternatives invalid |
| Jev / `TypeSafe/jev-1.13.0` | 200 | 28010 | 27105 | false / research / weak / no-valid-outline | needs_research; both alternatives invalid |
| OpenAI / `OpenAI/gpt-4.1` | 200 | 33729 | 33324 | true / b / strong / usable-outline | usable Cuban alternative b; seafood a invalid |

| Engine | Geocode ms | Discovery ms | Research ms | Plan ms | Decision stage ms | Stage sum ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local | 1535 | 12877 | 12085 | 5957 | 25 | 32479 |
| Jev | 658 | 8245 | 13336 | 4268 | 554 | 27061 |
| OpenAI | 190 | 7997 | 18302 | 5304 | 1486 | 33279 |

These are independent small-sample functional observations with changing public evidence and model outputs. Stage times mix network, provider tools, model work and Java execution. The decision stage includes both ask and classification. Browser wall time also includes submit mechanics and HTTP overhead. No pure model latency or controlled speed/quality ranking is claimed.

Raw actual API bodies: [local](live-fort-lauderdale-2026-10-14-local.json), [Jev](live-fort-lauderdale-2026-10-14-jev.json), [OpenAI](live-fort-lauderdale-2026-10-14-openai.json). These initial outputs are preserved independently of any later correction.

## Actual itinerary evidence

- Local: OB House breakfast 08:00–09:15 → NSU Art Museum sight 11:10–13:00 → same museum lunch 13:05–14:00. Second plan OB House 08:00–09:10 → Hugh Taylor Birch State Park sight 09:30–12:00 → same park lunch 12:10–13:00. Both invalid: travel/parking/walking gaps; lunch at sights lacks meal evidence. Kelly's Landing seafood and Cuban Guys were researched with unknown hours and no supported meal service, then omitted. OB House map tag says weekday 07:00–13:00, but initial research interpreted Tuesday as 08:00–13:00.
- Jev: OB House 07:30–08:45 → NSU Museum 11:00–12:10 → Mr. & Mrs. Crab seafood 12:30–13:50. Second: OB House 07:30–08:45 → Hugh Birch park 09:15–10:50 → Mario's Catalina Cuban 11:15–12:30. Both invalid: seafood gap 20 minutes versus 23 driving + 10 buffer; Cuban gap 25 versus 23 + 10. Distinct liked cuisines and venue-derived titles are present, but neither outline is usable.
- OpenAI selected b: OB House 07:40–08:40 → NSU Museum 11:05–12:15 → Padrino's Cuban 12:40–13:40. Requested breakfast/lunch and sight are timed inside the window, with empty blockers and advisory notes. Alternative a ends the museum at 12:25 then starts seafood at 12:45: 20 minutes versus 23 driving + 10 buffer, so invalid. No Italian-labelled stop was selected.
- Every breakfast is supported by `attributed_research_claim`; OB House did **not** supply `breakfast=yes` in these live map tags. No generic missing-breakfast-service blocker appeared. This live evidence does not exercise the mapped-breakfast-tag branch; that branch is covered by model-free tests.
- Sources include actual OSM links, NSU official site/admission page, Mr. & Mrs. Crab official site/menu, Mario's Catalina official site and Padrino's Fort Lauderdale location page, plus Wikipedia search leads. Attributed hours/meal claims remain unverified. The UI labels their basis and separates blocking issues from price/menu/current-hours advisory notes.

## Browser and terminal paths

Actual live DOM inspected at desktop 1440 × 1000 and mobile 390 × 844 for every engine. Document widths were exactly 1440 and 390, with no horizontal overflow. No `undefined`, `NaN`, stale hash/retry copy or JavaScript exceptions occurred. Real OpenAI selected card count was one; local/Jev zero. All rendered source links used HTTPS. Screenshots visually preserve the established design; lengthy advisory details remain readable and research is collapsible.

Actual early-start request with breakfast/coffee/neither unset: HTTP 200 `needs_input`, `processMs: 0`, three functional follow-up buttons. Complete invalid engine: HTTP 400 `INVALID_INPUT`, “Choose a listed decision engine”. Past date: HTTP 200 `needs_input`, `processMs: 0`, “Choose a current or future date in the destination city.” Sparse body `{engine: invalid}`: HTTP 502 `RESEARCH_UNAVAILABLE`, a backend validation defect routed to the core cook; this was not a pass. No exception details or private text appeared in that error.

Fixture-only browser cancellation re-enabled submit, retained the cancellation explanation after a delayed response, and stated that already-running server work may finish. Fixture-only HTTP 400 rendering showed a safe error and re-enabled submit. The only initial happy-flow console error was missing favicon HTTP 404; corrected in assigned HTML with an inline SVG icon. A rebuilt-package browser load returned `errors: []`, `inlineIcon: true`, `dateInitiallyBlank: true`; that follow-up submitted no itinerary. The fixture error naturally produced a browser HTTP 400 console entry; it is not an unhandled JS exception.

## Verification and limits

`mvn test package` before and after the favicon fix: 27 tests, zero failures/errors/skips, BUILD SUCCESS (8.671 and 12.584 seconds). `node --check src/main/resources/static/app.js` and `git diff --check`: exit 0. Exact production/static probe for replay, Tripper-port, old package, input hash/retry and removed renderer field names: no matches (rg exit 1). No live-check shell script was needed; actual Chrome/Playwright submissions used the installed runtime with no installs or dependencies.

All 15 initial stage logs have outcome=completed and contain only stage/timing/outcome values. Scans found neither runtime key, exact starting location nor city text in the application stage log, neither key in saved raw JSON, and no private dietary field in saved responses. Empty dietary text means live medical-text exclusion was not independently exercised; model-free privacy tests cover that boundary. Root ERROR/application INFO defaults were used; HTTP/model/prompt debug logging stayed disabled.

Local reviewer evidence remains in `/tmp/bayes-guide-integration-server.log`, `/tmp/bayes-guide-browser.cjs`, `/tmp/bayes-guide-browser-output.log`, `/tmp/bayes-guide-{local,jev,openai}-{desktop,mobile}.png`, `/tmp/bayes-guide-{engine}-dom.txt`, `/tmp/bayes-guide-{engine}-observation.json` and `/tmp/bayes-guide-integration-build.log`. These temporary paths are not portable deliverables. Signed commits and repository result JSON are the durable evidence. Both owned application sessions were stopped with Ctrl-C; their traps stop only the owned Java PIDs. No push, merge, publication, article modification or unrelated file changes.

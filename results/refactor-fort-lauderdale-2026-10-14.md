# Fort Lauderdale integration observation — 2026-10-14

Full three-engine acceptance remains **blocked after fourteen recorded outing submissions**. The latest two approved UI outings at `633590a` returned an unresolved local duplicate-choice result and two usable Jev Cuban alternatives, with Jev coverage still `false`. The latest OpenAI outing remains the earlier usable Cuban outline measured at `03f1bea`; OpenAI was not rerun after the candidate/feedback changes. All earlier failures remain preserved.

## Setup and count

Initial source: signed UI/docs commit `83be95a`, backend predecessor `0101de5`; preserved UI/evidence patches rebased as `a07233f`/`23a2eeb` after correction. Corrected source: `1fe1584`, reviewed backend `186c621`. Packaged `target/bayes-0.1.0.jar`, owned local port 18085 (18084 occupied), startup 3.302 seconds. Existing inherited runtime keys were used; neither was printed or copied. Discovery/research `gpt-4.1-mini`, planner `gpt-4.1`, same across all engines. Decisions independently selected local Java rules, named Jev or named OpenAI service.

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

`mvn test package` before and after the favicon fix: 27 tests, zero failures/errors/skips, BUILD SUCCESS (8.671 and 12.584 seconds). `node --check src/main/resources/static/app.js` and `git diff --check`: exit 0. Exact production/static probe for replay, Tripper-port, old package, input hash/retry and removed renderer field names: no matches (rg exit 1). Actual Chrome/Playwright submissions used the installed runtime with no installs or dependencies. A managed API script was subsequently prepared; shell syntax and past-date rejection passed without provider calls. The script was not live-executed, to keep the provider budget bounded.

All 15 initial stage logs have outcome=completed and contain only stage/timing/outcome values. Scans found neither runtime key, exact starting location nor city text in the application stage log, neither key in saved raw JSON, and no private dietary field in saved responses. Empty dietary text means live medical-text exclusion was not independently exercised; model-free privacy tests cover that boundary. Root ERROR/application INFO defaults were used; HTTP/model/prompt debug logging stayed disabled.


## Corrected build and bounded live observation

The independently reviewed native planning-feedback and safe malformed-binding correction was rebuilt: `mvn test package`, 30 tests, zero failures/errors/skips, BUILD SUCCESS in 7.295 seconds. JS/shell syntax and diff checks passed. The corrected app started in 2.241 seconds on owned port 18086 (18085 occupied). Same fixed outing and same discovery/research/planner defaults; exactly three corrected real browser submissions, local → Jev → OpenAI. **Six live outings through this correction, zero warmups, no optional reruns.** Initial JSON files were verified byte-for-byte unchanged against `23a2eeb`.

| Corrected selected engine | HTTP | Browser wall ms | Backend ms | Coverage / choice / confidence / classification | Actual result |
| --- | --- | ---: | ---: | --- | --- |
| local | 502 | 29785 | not returned | decision not reached | RESEARCH_UNAVAILABLE; research IllegalStateException |
| Jev / `TypeSafe/jev-1.13.0` | 200 | 25359 | 25301 | false / b / possible / material-gaps | unresolved b; invalid a missing requested lunch |
| OpenAI | 502 | 21543 | not returned | decision not reached | RESEARCH_UNAVAILABLE; research IllegalStateException |

| Corrected engine | Geocode ms | Discovery ms | Research ms/outcome | Plan ms | Decision stage ms | Stage sum ms | Actual plan-review calls |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| local | 850 | 11572 | 15889 / failed | not reached | not reached | 28311 | 0, planning not reached |
| Jev | 206 | 9661 | 10182 / completed | 4725 | 467 | 25241 | 0, planning reached but no review logged |
| OpenAI | 177 | 8287 | 12166 / failed | not reached | not reached | 20630 | 0, planning not reached |

The actual log format in the reviewed source is `stage=plan-review review=N durationMs=... outcome=invalid|unresolved|usable`. There are **zero** such lines in the corrected runtime log. Jev's model returned a draft without calling the native review tool; tool use is not established by configuration or model-free tests. The failed local/OpenAI research guards expose only `failureType=IllegalStateException` in safe logs; the precise guard/rejected research content is unavailable. No debug, prompt or HTTP logging was enabled and no cause is invented.

Corrected raw actual API bodies: [local](live-fort-lauderdale-2026-10-14-corrected-local.json), [Jev](live-fort-lauderdale-2026-10-14-corrected-jev.json), [OpenAI](live-fort-lauderdale-2026-10-14-corrected-openai.json). The two HTTP failures have only the safe code/message and no decision/model/timing fields; they are not represented as passes.

Corrected Jev selected b: OB House breakfast 07:30–08:30 → Kelly's Landing seafood lunch 11:35–12:30 → NSU Museum sight 12:45–13:45. Trusted drives 5/6/5 minutes plus ten-minute buffers fit. Meal slots and sight are timed, but Kelly's hours and lunch service are unknown, so both are explicit blocking questions. Alternative a has OB House breakfast 07:30–08:30 → NSU Museum 11:10–13:00 and omits requested lunch, so invalid. Cuban Guys remains researched with unknown hours/service; no usable seafood/Cuban diversity is established. Tuesday OB House research now correctly interprets weekday 07:00–13:00. Breakfast again uses attributed research, not a mapped `breakfast=yes` tag. No generic missing-breakfast-service blocker appeared. NSU official site/admission evidence and actual OSM source links render; Kelly's/Cuban Wikipedia pages are labelled search leads and do not establish meal service or hours.

| Observation / engine | Decision ask ms | Classification ms |
| --- | ---: | ---: |
| initial local | 11.806 | 0.420 |
| initial Jev | 390.881 | 163.179 |
| initial OpenAI | 976.851 | 505.807 |
| corrected local | not reached | not reached |
| corrected Jev | 280.416 | 167.851 |
| corrected OpenAI | not reached | not reached |

Numeric decision values come from actual response fields. Stage durations include provider/model/network/app work and stage sums omit orchestration/HTTP overhead; no controlled timing ranking is implied.

All corrected DOMs were inspected at 1440 × 1000 and 390 × 844: document widths equal viewport widths, no overflow/stale fields or JavaScript exceptions. Jev has one selected unresolved card; local/OpenAI safely render their actual failure. The page-console error list accumulates across the same browser session: two real HTTP 502 entries plus the final intentional fixture HTTP 400 entry. There are no favicon failures. The earlier 502 remains in Jev's accumulated console list and is not an extra Jev request failure. Fixture-only cancel/stale response/error checks passed again without provider calls.

Actual corrected early-choice request: needs_input200/processMs0 with three follow-up buttons. Sparse body now400 INVALID_INPUT with the constant safe message. Subsequent empty/null/truncated/sparse-body probes each400 with the same safe message; stage-line count stayed 11 → 11, proving zero extra stage work for those four probes. Alongside initial no-cost checks, these are terminal validation requests, not additional live outings.

Corrected safety scans: runtime keys absent log/raw JSON; exact origin/city absent stage log; private dietary field absent responses. All 11 corrected stage lines are present: nine completed and two failed research lines; zero plan-review lines. The private dietary field remains empty, so live medical-text exclusion is not independently exercised. Model-free privacy tests passed. Owned PID 98518/session was stopped with Ctrl-C/trap after checks (exit143). No provider reruns, publication, push, merge or article edits.


## Final capped round: GPT-4.1 research and mandatory actions

Reviewed backend `92a2759`; integration rebased at `b45d636`. Current defaults and this round explicitly pin both discovery/research and planning to `gpt-4.1`. The named decision services are unchanged. This model configuration differs from the earlier six outings using `gpt-4.1-mini` for discovery/research, so elapsed times do not establish a comparison. Own `mvn test package`: 35 tests, zero failures/errors/skips, BUILD SUCCESS in 7.520 seconds; JS/shell syntax and diff checks passed. Owned port 18087/PID 13780, startup 2.015 seconds.

Exactly three final independent real browser submissions, local → Jev → OpenAI. **Nine total provider-backed outing submissions; submission cap exhausted, zero timing warmups, no optional reruns.** All earlier six raw JSON files were verified byte-for-byte unchanged against `b45d636`. Final actual bodies: [local](live-fort-lauderdale-2026-10-14-final-local.json), [Jev](live-fort-lauderdale-2026-10-14-final-jev.json), [OpenAI](live-fort-lauderdale-2026-10-14-final-openai.json).

| Final selected engine / reported decision model | HTTP | Browser wall ms | Backend ms | Coverage / choice / confidence / classification | Result |
| --- | --- | ---: | ---: | --- | --- |
| local / local/bayes-itinerary-rules-v2 | 200 | 27388 | 25938 | false / research / weak / no-valid-outline | needs_research; both alternatives invalid |
| Jev / TypeSafe/jev-1.13.0 | 200 | 26641 | 26267 | false / research / weak / no-valid-outline | needs_research; both alternatives invalid |
| OpenAI | 502 | 39754 | not returned | typed fields not returned | DECISION_FAILED; complete typed answer unavailable |

| Final engine | Geocode | Discovery | Research | Draft | Mandatory review | Final action | Decision stage | Stage sum ms | Ask / classification ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| local | 1517 | 9652 | 5780 | 3804 | 969 | 4074 | 22 | 25818 | 7.190292 / 0.238291 |
| Jev | 179 | 7827 | 8310 | 3514 | 927 | 5022 | 401 | 26180 | 234.115875 / 166.484625 |
| OpenAI | 184 | 9360 | 17576 | 4565 | 1080 | 4588 | 1402 | 38755 | not returned |

The actual completed duration lines prove one `plan-draft`, one mandatory `plan-review` and one `plan-final` action for **each** request. Each review also emits a findings line (`invalid=2 unresolved=0`); two review log lines mean one action, not two invocations. Each `plan-refinement requested=true` line proves a single requested refinement in the final action. The reviewed source returns the final Java validation from `plan-final`; its completion establishes that final guard ran. This is a mandatory typed action dependency, not an optional LLM review tool. Final OpenAI's completed decision stage does not imply typed-response success: the service returns safe `DECISION_FAILED` afterward, and does not expose which answer field was incomplete.

**Actual semantic evidence remains wrong, not merely incomplete:** final local maps NSU Art Museum to “Popular cafe” with 07:00–18:00 hours although its mapped Tuesday hours are 11:00–17:00; Museum of Discovery and Science is interpreted as a seafood restaurant; Hugh Taylor Birch State Park as a breakfast cafe; OB House as an art museum; Kelly's mapped seafood as Cuban cuisine; Padrino's as a coffee shop opening 07:00 despite mapped Tuesday 11:30. All eight final-local research views have zero retrieved pages and cite their own mapped IDs. Correct source-ID membership does not establish that the attached claim matches that venue. Claims remain labelled unverified, but these contradictions fail useful-flow acceptance.

Final local alternatives start park breakfast 07:00–08:10 immediately at the 07:00 origin departure, ignoring 11 driving minutes plus ten-minute buffer; follow with OB House as a sight and Museum of Discovery/Science or Kelly's as lunch. Both invalid. Final Jev puts Padrino's breakfast at 07:00–07:50, Cafe Siena as a sight and Mario's as seafood lunch; its other option puts Mr. & Mrs. Crab as a French/bakery breakfast venue, Cafe Siena as a sight and NSU Museum as lunch. Both invalid travel gaps. Venue-derived titles correctly display the actual proposed mapped names, so the contradictions stay observable. No complete supported breakfast/lunch or cuisine diversity is established by this round. The mapped `breakfast=yes` branch remains fixture-covered, not exercised by these outputs.

Safe research normalization logs actually report local INVALID_WINDOWS=2/UNSUPPORTED_HOURS=2 and Jev INVALID_FIELDS=8/INVALID_WINDOWS=3/UNSUPPORTED_HOURS=3; those unknown/omitted fields remain visible. Despite that partial validation, unrelated venue-role/hour/cuisine claims survive, and the one refinement does not produce valid plans. No further backend correction or provider retry was made by this integration cook.

Actual final desktop/mobile DOM widths 1440/390 equal viewport widths; no horizontal overflow, stale fields or JavaScript exceptions. Local/Jev page consoles have no errors; OpenAI shows its actual HTTP 502. The later fixture-only error adds the expected HTTP 400 console entry. Real early-choice needs_input200/processMs0 and sparse-binding400 remain safe; fixture-only cancel/stale-response/error rendering passed with no extra provider work. Final local desktop and Jev mobile screenshots visibly retain the established design and blocker/advisory distinctions. Independent review found the final local-mobile and OpenAI-mobile PNGs repeat the hero section vertically; those two captures are incomplete visual evidence and do not establish complete mobile appearance. DOM width checks remain valid, but no further captures or calls were made.

Offline final scans found no runtime keys in logs/raw JSON, no exact origin/city in stage logs and no private dietary field in responses. Empty dietary text limits live medical-exclusion evidence. Owned final server stopped with Ctrl-C/trap (session exit143); no other app was stopped. No further API calls after the outing-submission hold, no push/merge/publication/article edits. The README and managed script now reflect current GPT-4.1 defaults, mandatory review and partial-unknown semantics; the script was not live-executed.


## Subsequent offline evidence and decision corrections

Reviewed commits `0b749f2` and `1f36f94` are now in the integration source (`6cb9714` before this documentation update). No provider calls or backend startup occurred after the nine recorded outings. These captures are unchanged and precede the corrections. The narrow independent review recorded 16 focused tests passing with zero failures/errors/skips and an offline package pass. It covered mandatory evidence dependencies and cache/privacy fixtures, ordered confidence levels, no-choice service bypass and fixed typed failure reasons. This is offline source/fixture evidence, not proof of actual model behavior or useful live acceptance after the fix.

`retrieveEvidence` now mandatorily reads each selected venue before research. Its typed payload binds exact mapped ID/name/category/tags/map source and retrieved pages to that venue. Research, initial planning and final refinement consume that same evidence. The fixtures check associations against captured mapped fields; mocked fixture pages are not new live evidence. Missing evidence stays unknown.

Confidence order is now weak → possible → strong. Empty/all-invalid alternatives return false/research/weak/no-valid-outline with model not-called, zero decision/classification times and zero DecisionService interactions. Valid candidates still use the selected service. Fixed UNAVAILABLE/INVALID_RESPONSE reason codes distinguish typed failures without logging raw responses. The old OpenAI502 contains no rejected answer details; these fixes do not establish its exact cause.

The historical budget was **nine outing submissions**, not nine raw provider calls. Zero outing resubmissions does not mean zero internal provider retries. Installed Embabel settings default to OpenAI maxAttempts=10, typed data-binding maxAttempts=10 and LLM decisions maxAttempts=5. The managed script now overrides only those three retry templates to one attempt. App configuration is unchanged; historical runs did not use the new overrides. Tool conversation turns and compatibility fallback can still add HTTP calls. The harness now starts with local, proceeds to Jev/OpenAI only while preceding results are usable, and stops after the first HTTP/connection/non-usable failure. It reports the actual count, up to three outing submissions. The harness was syntax-checked only and was not executed.

Researcher output limit is 3000 tokens per response; planner output limit is 2500. Public page excerpts allow up to eight venues × two pages × 6000 characters. There is no aggregate token/spend cap in this app or script. Raw provider-call counts, token usage and aggregate cost were not collected; no usage or cost estimate is inferred. Further paid calls remain on hold, and no completion or publication claim follows from the offline corrections.

Current offline documentation checks passed: Bash syntax, JavaScript syntax, diff whitespace and 13 local README/result links (zero missing). The rebased integration package compiled offline with tests skipped; no full-suite rerun or harness execution occurred. Tracked production Java source contains 23 files, 1674 lines and 109648 bytes. This source-size count includes blank lines and comments and excludes tests, dependencies and generated target files. The actual signed documentation commit and its final package check are recorded in the private cook report. Live usability after the evidence/decision fix remains unverified.

## Approved post-fix batch at 03f1bea

James approved exactly three additional outings. The same synthetic Fort Lauderdale scenario was submitted once each for local, Jev and OpenAI. All three attempts completed; no warmups or resubmissions. The private ledger records all three finished responses. **Twelve total outing submissions are now recorded.** This is not a raw provider-call, token or cost count.

Measured source: `03f1bea0c4f828184f6cc8733cef611a61059a1f`, including evidence/decision fixes `0b749f2` and `1f36f94`. Its existing offline skip-tests package passed in 1.821 seconds. No suite/package was rerun for this capture-only update. Research/planning were explicitly pinned to GPT-4.1. OpenAI, data-binding and LLM-decision retry templates were each set to one attempt. The compatibility-fallback logger was enabled at WARN; no known unsupported-parameter fallback warning was observed. That fallback was not disabled, and raw HTTP counts remain unknown.

| Selected engine | HTTP | Browser wall ms | Backend ms | Actual decision/result |
| --- | --- | ---: | ---: | --- |
| local | 200 | 37642 | 37558 | needs_research; false/research/weak/no-valid-outline; model not-called; ask/classification 0 ms |
| Jev | 400 | 3160 | not returned | INVALID_INPUT: Unknown mapped place ID; discovery failed before Jev |
| OpenAI | 200 | 34381 | 34341 | usable b; true/b/strong/usable-outline; OpenAI/gpt-4.1; ask1194.899541/classification476.817792 ms |

| Engine | Geocode | Discovery | Evidence | Research | Draft | Review | Final | Decision stage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| local | 862 | 7963 | 7633 | 8152 | 5298 | 1099 | 6333 | 0 |
| Jev | 734 | 2375 failed | not reached | not reached | not reached | not reached | not reached | not reached |
| OpenAI | 174 | 8239 | 7164 | 6453 | 4490 | 1053 | 4991 | 1684 |

Local and OpenAI each completed mandatory evidence, draft, review and final actions, with one refinement. Their initial review found two invalid drafts. Actual evidence retrieval produced nine page records/five retrieved venue pages for local. The source action passes exact venue identity/tags/pages into research and both planning prompts. Actual associations now match OB House breakfast, NSU art museum and the named Cuban/seafood restaurants; earlier cafe/museum substitutions are absent in these two responses.

Local still failed final timing. Both alternatives depart the origin at 07:00 and reach OB House 07:30–08:30; the 30-minute gap exceeds its five-minute drive plus ten-minute buffer. Alternative a lunches at Padrino’s 11:50–12:50, then starts NSU Museum at 13:05: gap15 versus drive8+10=18, deficit3 minutes. Alternative b lunches at Mr. & Mrs. Crab 12:10–13:10, then starts NSU at 13:25: gap15 versus drive22+10=32, deficit17. Both are invalid. The model rationale says transfers fit, but final validation rejects them. The no-valid-choice guard correctly reports not-called with zero DecisionService times.

The Jev-selected request failed discovery with the fixed message “Unknown mapped place ID”. The existing mapped-place lookup guard emits that message. The rejected ID and exact selection/tool operation were not captured, so no more specific cause is claimed. The response labels this model/discovery failure INVALID_INPUT; it is not evidence that the supplied form fields were wrong. No evidence, planning or Jev decision was reached.

OpenAI selected b: OB House breakfast07:30–08:30 → NSU Museum11:00–12:00 → Padrino’s Cuban lunch12:20–13:20. Incoming gaps30/150/20 meet trusted drive-plus-ten requirements15/12/18. Each slot fits the actual mapped Tuesday window: OB07–13, NSU11–17, Padrino11:30–21. Mapped category/cuisine fields and attributed service match the proposed venues; NSU and Padrino also have retrieved official page evidence. No selected blocker remains. Alternative a uses Kelly’s seafood lunch12:20–13:20 and stays unresolved because hours and lunch service are unknown. Those two questions remain visible. This is a supported provisional outline, not independently verified current availability, a booking or allergy safety. Seafood/Cuban alternatives are present, but only the Cuban option is usable.

New snapshots: [local](live-fort-lauderdale-2026-10-14-postfix-local.json), [Jev](live-fort-lauderdale-2026-10-14-postfix-jev.json), [OpenAI](live-fort-lauderdale-2026-10-14-postfix-openai.json). Sixteen retrieved/search-lead `PageEvidence.excerpt` strings are omitted from these three files. Each retains its URL/ID/status and adds `originalCharacterCount` plus a UTF-8 SHA-256 `contentSha256`. All other fields, statuses and timings are unchanged. Full responses remain private for review; existing nine snapshots are unchanged.

All six complete result screenshots were captured live from the active response UI before any packaging error. Desktop/mobile views show the actual two invalid local cards, safe Jev error and selected OpenAI card with a separate unresolved seafood option. Local/Jev numeric DOM width checks show1440/390 with no overflow or stale fields; their JavaScript error captures are empty. OpenAI’s saved DOM and complete screenshots align with its response. Its numeric DOM/error-stream observation was not saved.

A temporary log-path pause introduced an EISDIR error while packaging the final OpenAI observation, after all three responses, public snapshots, DOM files and screenshots had been saved. No screenshots or UI were recreated afterward, and no requests were retried. Existing stage logs provide the OpenAI action timings above. This is a measurement-packaging limitation, not an OpenAI API failure. The owned backend was stopped after the batch. Full three-engine acceptance remains blocked by local scheduling and Jev discovery; no later offline patch has paid verification in these records.

## Subsequent offline candidate and timing-feedback changes

Signed commits `636cbf5` and `82a5a66` change the mandatory action order to `gatherCandidates` → tool-free model selection → `retrieveEvidence`. Gathering establishes mapped IDs before the model selects venues. Retrieved evidence still carries exact identity, tags and pages into research and both planning actions. An unknown model-selected ID now returns an internal HTTP 502; invalid user input remains HTTP 400. This corrects the error classification seen in the Jev-selected capture above. It does not demonstrate a successful new Jev outing.

Travel review now supplies each leg’s departure, trusted drive time, ten-minute buffer, earliest arrival and exact shortfall. The captured local examples produce `12:50 + 8 + 10 = 13:08` versus requested `13:05` (three minutes short), and `13:10 + 22 + 10 = 13:42` versus requested `13:25` (17 minutes short). One refinement remains the limit; final validation still rejects an invalid result. The validator does not move stops automatically. Offline fixtures preserve these IDs and route values on a future fixture date, and verify that starts at 13:08 and 13:42 fit.

Independent review at `82a5a66` passed 20 focused tests: zero failures, errors or skips; `BUILD SUCCESS` in 3.533 seconds. The independent offline package with tests skipped also passed in 1.565 seconds. These are recorded fixture and compile checks, not new provider results. Current production Java comprises 23 files and 1703 physical lines, including comments and blank lines; this documentation change does not alter Java.

**Checkpoint before the final two outings:** no outing had yet been submitted after these changes. All twelve recorded submissions and their failures remain unchanged. In the latest three-request batch, only the OpenAI Cuban outline was usable; local timing and Jev-selected discovery prevented full acceptance. Its exact measured source remains `03f1bea0c4f828184f6cc8733cef611a61059a1f`. No raw provider-call, usage or cost count is inferred. At that checkpoint no further paid calls were authorized, and those offline results did not establish complete-refactor acceptance. The subsequent explicit two-outing approval and actual outcomes are recorded below.


## Final two approved UI outings on current source

Exact measured source: signed `633590ab1096321c7b284813516d51e60ee0c024`, including reviewed candidate/feedback commits `636cbf5` and `82a5a66`. The existing exact-source offline package passed in 1.866 seconds with tests skipped; it was reused after package/resource alignment checks. No build or full suite was repeated. The owned backend used unused port 18089 and started in 2.449 seconds. Both discovery/research and planning were explicitly GPT-4.1. Existing runtime credentials were inherited without printing or persistence.

Exactly two additional actual form submissions, local then Jev, brought the recorded total to fourteen. The new ledger records authorized=2, attempted=2, countBefore=0/1, ordinal=1/2, zero warmups and zero outing resubmissions. Local began at 2026-10-14T11:36:05.899Z; Jev at 2026-10-14T11:37:23.345Z. No OpenAI outing or decision call was made in this batch; both outings still used OpenAI research and planning. The same synthetic date, 07:00–14:00 window, two adults, art, steady pace, breakfast/lunch, seafood/Cuban likes, Italian dislike and both consents were used. Private dietary and profile fields were empty.

| Selected engine / actual decision model | HTTP | Browser wall ms | Backend ms | Coverage / choice / confidence / classification | Actual result |
| --- | --- | ---: | ---: | --- | --- |
| local / `local/bayes-itinerary-rules-v2` | 200 | 45671 | 45571 | false / a / possible / material-gaps | unresolved; both alternatives duplicate venue/order |
| Jev / `TypeSafe/jev-1.13.0` | 200 | 43446 | 43403 | false / b / possible / usable-outline | selected b usable; a also usable; coverage false retained |

| Engine | Geocode | Candidates | Discovery | Evidence | Research | Draft | Review | Final | Decision stage ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| local | 1482 | 18564 | 2544 | 6517 | 6282 | 4386 | 958 | 4655 | 22 |
| Jev | 788 | 17644 | 1675 | 6289 | 7418 | 3870 | 907 | 4072 | 543 |

Each request completed exactly one candidate, discovery, evidence, research, draft, mandatory review, final and decision action. Each initial review recorded invalid=2/unresolved=0 and requested exactly one refinement. Final validation retained the actual outcomes. Local ask/classification took 9.061708/0.330166 ms; Jev 378.430292/163.893042 ms. These are functional observations, without a performance ranking or isolated model-latency claim.

Local selected a: OB House breakfast 07:30–08:30 → NSU Art Museum 11:00–12:45 → Padrino’s Cuban lunch 13:03–14:00. Incoming gaps 30/150/18 minutes meet trusted drive-plus-ten requirements 15/12/18. The final lunch starts at the exact required arrival time. Alternative b uses 08:00–09:00 → 11:05–12:50 → 13:23–14:00, with gaps 60/125/33 against 15/12/18. Both fit attributed Tuesday windows, but their venues and order are identical. Both remain unresolved with the material question “Provide distinct alternatives with different venues or stop order; shifting times alone is not another choice.” The selected card visibly retains that blocker. This is not full local acceptance.

Jev selected b: OB House breakfast 07:15–08:15 → NSU Art Museum 11:10–12:40 → Padrino’s Cuban lunch 13:10–14:00. Gaps 15/175/30 meet requirements 15/12/18. Distinct alternative a uses the same breakfast, NSU 11:10–13:00 and Mario’s Catalina Cuban lunch 13:30–14:00; gaps 15/175/30 meet 15/12/26. Both alternatives are usable with empty blocking questions. Jev’s actual coverage answer is `false`, rating `possible`, choice `b`, classification `usable-outline`; the response and UI preserve these fields. The selected usable outline does not establish an affirmative coverage decision, and no cause for that false answer is invented.

All selected stop IDs match mapped names/categories: OB House breakfast `W551457772`, NSU museum `W532105760`, Padrino restaurant `N9796646021`, and Jev alternative a’s Mario restaurant `N14202541907`. Source IDs belong to their exact mapped venue or its retrieved page. Tuesday windows are OB 07:00–13:00, NSU 11:00–17:00, Padrino 11:30–21:00 and Mario 11:00–22:00; every timed slot fits. Meal service uses `attributed_research_claim`; OB still has no mapped breakfast=yes tag. Cuban cuisine matches restaurant map tags. No Italian-labelled stop is selected. Seafood candidates Southport and Kelly’s retain unknown hours and lunch service and are omitted, so this batch does not establish usable seafood/Cuban diversity. Routine menu, admission and check-before-visiting notes remain advisory. Operating windows and retrieved pages remain unverified current evidence.

New actual sanitized snapshots: [local](live-fort-lauderdale-2026-10-14-final-two-local.json) and [Jev](live-fort-lauderdale-2026-10-14-final-two-jev.json). Their eleven page excerpts are omitted; exact IDs, URLs and statuses remain with original character counts and UTF-8 SHA-256 digests. Local retrieved three venue pages among five page records; Jev four among six. Full raw responses, DOM text, stage observations and ledger remain private. All twelve earlier response files and the thirteen protected user files were checked unchanged.

Both actual result DOMs and viewport/complete screenshots were captured at desktop 1440×1000 and mobile 390×844 before closing each browser. Document widths equal viewport widths; each result has one selected card, no stale fields, no JavaScript exceptions and no console errors. Source links number 44/47. The screenshots show local’s blocking duplicate-choice question and Jev’s selected usable card with the false coverage answer. No result was reloaded or recreated. Both browsers closed through finally; only the owned backend was stopped after the two outings.

The three configured retry templates were explicitly one attempt. A temporary standard Logback appender emitted only a constant `COMPATIBILITY_FALLBACK` marker for the installed InstrumentedChatModel WARN logger, with no raw message or exception. No marker occurred. That unsupported-parameter compatibility fallback is not configurable off by these settings; tool turns and raw HTTP call counts were not measured. No aggregate token, usage or monetary cost cap is claimed.

**Historical verdict before the duplicate-policy correction:** the review required distinct usable local choices and affirmative Jev coverage. The clarified application contract permits one validated itinerary; the follow-up below assesses that contract without changing these recorded responses. Seafood readiness remains unknown, and the latest OpenAI result is from `03f1bea`, not current source. The exact two-outing approval is exhausted. No retries, third outing, publication or article edits were performed.


## Offline correction of equivalent alternatives

The application returns up to two validated itineraries. Two drafts with the same ordered stop kinds and mapped venue IDs represent one choice. The validator now checks each schedule before retaining a stable representative, preferring usable over unresolved over invalid. It preserves the winning ID, times, venue identity and sources; duplicate removal adds an advisory note. Required hours, route, meal-service and access checks remain in force. No second itinerary is manufactured and no new refinement or retry is added.

The [captured-plan assessment](offline-captured-validation-2026-10-14.json) records input SHA-256 digests, the reviewed code commit and separate offline results. The local capture produces one usable alternative a with its original 07:30 breakfast, 11:00 museum and 13:03 Cuban lunch times. The real local decision implementation selects a from those corrected inputs. Jev’s recorded alternatives remain usable and distinct; its original false/b/possible/usable-outline answer is preserved. Coverage is a provider proposition, while selected itinerary status is determined by validated required needs. No rationale for Jev’s false answer was captured, so none is inferred.

This is a deterministic assessment of existing captured evidence, not a new live outing or remote decision benchmark. Fixture coordinates support route lookup keys; recorded drive durations remain the route answers. Removed third-party page excerpts stay omitted. Unknown seafood hours and service remain unknown. Earlier live responses, timings and failures are unchanged. The newest OpenAI decision observation still belongs to the earlier measured source.

The executable correction is signed commit `cd237234b63b947993d2b904aadc92d79cd2ffa1`; the assessment is committed separately at `b19e386d` so it can name that source without a self-referential hash. The full absent-key offline gate passed 46 tests with zero failures, errors or skips and packaged successfully in 7.069 seconds. Independent review passed 14 focused tests in 3.304 seconds, reproduced the captured regression against the earlier validator, and checked assessment parity and preserved input hashes. No post-correction live outing was submitted.

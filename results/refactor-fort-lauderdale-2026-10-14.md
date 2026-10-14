# Fort Lauderdale integration observation — 2026-10-14

Functional acceptance remains **blocked after the final capped round**. Across nine real outings, the latest local/Jev requests returned only invalid outlines with venue facts assigned to the wrong mapped places; final OpenAI returned a typed-decision HTTP 502. The final round proves mandatory review and a single refinement occurred for each request, but it does not establish a useful complete guide. Earlier failures and the initial OpenAI success remain preserved. Passing model-free tests is not live functional acceptance.

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

Local reviewer evidence remains in `/tmp/bayes-guide-integration-server.log`, `/tmp/bayes-guide-browser.cjs`, `/tmp/bayes-guide-browser-output.log`, `/tmp/bayes-guide-{local,jev,openai}-{desktop,mobile}.png`, `/tmp/bayes-guide-{engine}-dom.txt`, `/tmp/bayes-guide-{engine}-observation.json` and `/tmp/bayes-guide-integration-build.log`. These temporary paths are not portable deliverables. Signed commits and repository result JSON are the durable evidence. Both owned application sessions were stopped with Ctrl-C; their traps stop only the owned Java PIDs. No push, merge, publication, article modification or unrelated file changes.

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

Corrected supporting files: `/tmp/bayes-guide-integration-corrected-build.log`, `/tmp/bayes-guide-integration-corrected-server.log`, `/tmp/bayes-guide-browser-corrected.cjs`, `/tmp/bayes-guide-browser-corrected-output.log`, `/tmp/bayes-guide-corrected-{engine}-{desktop,mobile}.png`, `-dom.txt`, `-observation.json`. Independent source review also identified obsolete README cuisine-alias guidance; fixed to actual accepted `seafood`/`cuban` names. These temporary files complement durable checked-in raw outputs; they do not substitute for a useful live flow.

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

Final temporary evidence: `/tmp/bayes-guide-integration-final-build.log`, `/tmp/bayes-guide-integration-final-server.log`, `/tmp/bayes-guide-browser-final.cjs`, `/tmp/bayes-guide-browser-final-output.log`, `/tmp/bayes-guide-final-{engine}-{desktop,mobile}.png`, `-dom.txt`, `-observation.json`. Safe durable raw responses and this note retain every failure; they are not a usable guide or a performance ranking.

## Subsequent offline evidence and decision corrections

Reviewed commits `0b749f2` and `1f36f94` are now in the integration source (`6cb9714` before this documentation update). No provider calls or backend startup occurred after the nine recorded outings. These captures are unchanged and precede the corrections. The narrow independent review recorded 16 focused tests passing with zero failures/errors/skips and an offline package pass. It covered mandatory evidence dependencies and cache/privacy fixtures, ordered confidence levels, no-choice service bypass and fixed typed failure reasons. This is offline source/fixture evidence, not proof of actual model behavior or useful live acceptance after the fix.

`retrieveEvidence` now mandatorily reads each selected venue before research. Its typed payload binds exact mapped ID/name/category/tags/map source and retrieved pages to that venue. Research, initial planning and final refinement consume that same evidence. The fixtures check associations against captured mapped fields; mocked fixture pages are not new live evidence. Missing evidence stays unknown.

Confidence order is now weak → possible → strong. Empty/all-invalid alternatives return false/research/weak/no-valid-outline with model not-called, zero decision/classification times and zero DecisionService interactions. Valid candidates still use the selected service. Fixed UNAVAILABLE/INVALID_RESPONSE reason codes distinguish typed failures without logging raw responses. The old OpenAI502 contains no rejected answer details; these fixes do not establish its exact cause.

The historical budget was **nine outing submissions**, not nine raw provider calls. Zero outing resubmissions does not mean zero internal provider retries. Installed Embabel settings default to OpenAI maxAttempts=10, typed data-binding maxAttempts=10 and LLM decisions maxAttempts=5. The managed script now overrides only those three retry templates to one attempt. App configuration is unchanged; historical runs did not use the new overrides. Tool conversation turns and compatibility fallback can still add HTTP calls. The harness now starts with local, proceeds to Jev/OpenAI only while preceding results are usable, and stops after the first HTTP/connection/non-usable failure. It reports the actual count, up to three outing submissions. The harness was syntax-checked only and was not executed.

Researcher output limit is 3000 tokens per response; planner output limit is 2500. Public page excerpts allow up to eight venues × two pages × 6000 characters. There is no aggregate token/spend cap in this app or script. Raw provider-call counts, token usage and aggregate cost were not collected; no usage or cost estimate is inferred. Further paid calls remain on hold, and no completion or publication claim follows from the offline corrections.

Current offline documentation checks passed: Bash syntax, JavaScript syntax, diff whitespace and 13 local README/result links (zero missing). The rebased integration package compiled offline with tests skipped; no full-suite rerun or harness execution occurred. Tracked production Java source contains 23 files, 1674 lines and 109648 bytes. This source-size count includes blank lines and comments and excludes tests, dependencies and generated target files. The actual signed documentation commit and its final package check are recorded in the private cook report. Live usability after the evidence/decision fix remains unverified.

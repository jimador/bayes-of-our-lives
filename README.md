# Bayes of Our Lives

Bayes is a one-day city guide built with [Embabel Agent 1.5.4-SNAPSHOT](https://github.com/embabel/embabel-agent). Its typed actions discover mapped venues, research their evidence, propose timed alternatives and ask one selected `DecisionService` about coverage, choice, confidence and readiness. Real Dice repositories save and recall confirmed interests and pace.

## Run the city guide

You need Java 21, Maven, `curl`, the configured Embabel snapshot dependencies and **both existing runtime `OPENAI_API_KEY` and `TYPESAFE_API_KEY`**. The full configuration eagerly creates the named OpenAI and Jev services, even when you select local decisions. Research and planning use OpenAI for every engine. Keep credentials in your runtime environment, out of command arguments, browser fields, logs and repository files.

```sh
mvn test package
java -jar target/bayes-0.1.0.jar
```

Open **http://127.0.0.1:8080/**. Run the app in a foreground terminal or a managed terminal session; stop that session with Ctrl-C. To use a different port, append `--server.port=18084`. Stop only the process you started.

For a reproducible synthetic backend check, run `bash scripts/run-live-check.sh 2026-10-20` with both keys already in the environment. Change the destination-local date once that date has passed. The script checks the date and an unused port, builds the package, starts its own app, submits a local outing first, then Jev and OpenAI only while preceding results are usable, saves timestamped actual JSON and safe stage logs in a fresh temporary directory, then stops only its own process. It requires Python 3 in addition to the app prerequisites. `BAYES_LIVE_PORT` selects the local port (default 18085). It stops after the first HTTP, connection or non-usable result and reports the actual submission count, up to three. A nonzero exit reports that failure; it makes no outing resubmissions and sets three framework retry templates to one attempt. Tool conversations and compatibility fallback can still make additional HTTP calls. This API check complements the separate real browser validation. Running it makes real provider calls.

The named configuration in `src/main/resources/application.properties` references runtime keys:

```properties
embabel.agent.platform.models.openai.api-key=${OPENAI_API_KEY:}
embabel.agent.platform.decisions.llm.services.openaiDecision.llm=gpt-4.1
embabel.agent.platform.models.typesafe.api-key=${TYPESAFE_API_KEY:}
embabel.agent.platform.models.typesafe.services.jevDecision.model=jev-latest
embabel.models.decision.roles.prompted=openaiDecision
embabel.models.decision.roles.specialized=jevDecision
bayes.models.researcher=${BAYES_RESEARCHER_MODEL:gpt-4.1}
bayes.models.planner=${BAYES_PLANNER_MODEL:gpt-4.1}
```

Three separately injected itinerary services receive local Java rules, `jevDecision`, or `openaiDecision`. Each submission runs the same Embabel actions and calls only its selected decision service. `BAYES_RESEARCHER_MODEL` and `BAYES_PLANNER_MODEL` can select existing configured OpenAI models. Discovery/research and planning both default to `gpt-4.1`. The final integration round and managed script explicitly pin both to `gpt-4.1`; earlier recorded rounds used `gpt-4.1-mini` for discovery/research and cannot establish a timing comparison with the final configuration.

## Plan an outing

Fill in the city, starting point, destination-local date and time window, meals, group, interests and pace. **Choose today or a future date in the destination timezone**. Today's finish time must still be in the future there. The backend checks the geocoded timezone and rejects ambiguous daylight-saving windows. The form leaves the date for you to choose.

Cuisine likes and dislikes accept comma or semicolon separated cuisine names, such as `seafood` and `cuban`. They are preferences. **`dietaryOrAllergyNeeds` is the separate private restriction/allergy field.** Cuisine fields reject restriction wording. There is no automatic pizza → Italian inference. With consent, up to two cuisine likes can become public map search terms; cuisine likes and dislikes also go to the model. A cuisine preference does not establish allergy safety.

Public-map consent and remote-model consent are separate. The exact starting location goes to public map/route services. Models receive city, date/window, group, interests, pace, cuisine preferences, access requirements and venue evidence. Exact starting location and allergy/restriction text stay out of model prompts. Private restriction text is also excluded from public provider inputs and persisted preferences; it causes a direct-confirmation question for meals. Do not send private notes in interests or other public fields.

For an early start with no breakfast or coffee choice, Bayes asks you to choose breakfast, coffee or neither before provider work. Editing the form or choosing a follow-up option submits a new outing. Browser cancellation stops the browser request; backend work already started may finish.

`POST /api/itinerary` preserves the form contract:

```json
{
  "city": "Fort Lauderdale, Florida",
  "startingLocation": "Downtown Fort Lauderdale",
  "date": "2026-10-20",
  "startTime": "07:00",
  "endTime": "14:00",
  "breakfast": true,
  "coffee": false,
  "earlyFoodDeclined": false,
  "lunch": true,
  "dinner": false,
  "partySize": 2,
  "childrenCount": 0,
  "interests": "art",
  "pace": "steady",
  "stepFreeAccess": false,
  "cuisineLikes": "seafood, cuban",
  "cuisineDislikes": "italian",
  "dietaryOrAllergyNeeds": "",
  "engine": "local",
  "publicMapConsent": true,
  "remoteModelConsent": true
}
```

Change the example date when it is in the past. `engine` accepts `local`, `jev` or `openai`; each is an independent submission with new public evidence and agent work. Optional `profileId`, `rememberPreferences` and `useRememberedPreferences` support local preference recall. **Save interests and pace** stores those fields under a browser-generated profile ID in ignored `.bayes-data/preferences.json`; owner-only permissions are used on POSIX. Meals, group, access and dietary needs always come from the current request. Clear saved preferences by deleting that local file and the browser's `bayes-profile-id` local-storage entry.

## Read the result

The mandatory action order is `gatherCandidates` → tool-free model selection → `retrieveEvidence`. Candidate gathering establishes mapped venue IDs before selection. Evidence retrieval then produces typed `RetrievedEvidence` before research. It carries each selected venue’s exact mapped ID, name, category, tags, map source and retrieved pages into research, initial planning and final refinement. An unknown ID returned by the model is an internal failure with HTTP 502; invalid user input returns HTTP 400.

The Embabel action chain drafts an itinerary, reviews it with the Java validator, and finalizes it. Travel feedback gives the departure time, trusted drive duration, ten-minute buffer, earliest arrival and exact shortfall for each invalid leg. If any draft alternative is invalid or unresolved, a single model refinement receives that review, followed by final validation. A usable draft needs no refinement. The validator does not move stops automatically. This is a typed action dependency, not an optional review tool or an application retry loop.

A **usable** outline covers the requested stops and meals, with chronological times inside the outing window, attributed operating windows and trusted base routes. **Unresolved** means material evidence is missing; **invalid** means a requirement or known constraint fails. **Needs research** means the decision did not select an outline. The browser shows blocking issues separately from advisory price, menu and routine check-before-visiting notes. Titles come from the actual chosen venue names. Requested breakfast needs service evidence; a mapped `breakfast=yes` tag supports it provisionally without a generic missing-service question. Cafe or coffee labels alone do not establish breakfast.

Incomplete, duplicate or improperly attributed research records become explicit unknown interpretations. Invalid fields or unsupported operating windows are omitted; independently retrieved map/page evidence remains visible. These partial results can still yield an unresolved outline with material questions, rather than treating rejected model claims as facts.

Confidence levels are ordered `weak` → `possible` → `strong`. If no non-invalid alternative exists, Bayes returns coverage `false`, choice `research`, confidence `weak`, classification `no-valid-outline`, model `not-called` and zero decision/classification times. It makes no `DecisionService` call in that case. Other choices use the selected service. Typed failures retain fixed question/reason codes such as `UNAVAILABLE` and `INVALID_RESPONSE` without raw provider output. The underlying reason for the earlier OpenAI error was not captured.

Coverage, choice, confidence and classification describe the typed evidence fit. They do not verify bookings or availability. Cuisine diversity is checked when researched liked-cuisine candidates fit hours and routes; incomplete alternatives retain material questions. Unknown facts stay visible. Stop evidence includes source IDs, mapped links, route basis, meal-service basis, cuisine interpretation and hours basis. Expand the research section to inspect public pages and attributed interpretations.

## Sources, privacy and logging

Public adapters use [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/), [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md), [OSRM](https://project-osrm.org/docs/v5.24.0/api/), OSM-recorded HTTPS venue pages and Wikipedia search leads. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Venue-page requests validate public addresses, pin the checked IP, verify HTTPS and disable proxies/redirects. Provider tool calls are bounded and cached for each outing. External text and model output are untrusted evidence.

Operating windows are model interpretations of attributed map/page evidence, not verified current opening times. Search leads do not establish service or hours. Admission, menus, access, prices and safety can remain unknown. Driving estimates exclude live traffic. Confirm changes and private dietary/allergy needs directly with venues.

Root logging defaults to `ERROR`; application stage logging defaults to `INFO`. `BAYES_LOG_LEVEL=ERROR` silences the stage summaries, or use `--logging.level.dev.jimador.bayes=ERROR`. Stage logs contain stage name, duration, outcome, failure type and safe research-issue/review/refinement counts; they exclude request text, private origin, medical notes and credentials. Keep framework/model HTTP debug and prompt logging disabled when handling private requests.

## Limits and retry settings

The managed script starts the app with these one-attempt settings. The application configuration is unchanged.

| Setting | Installed framework default | Managed script |
| --- | ---: | ---: |
| `embabel.agent.platform.models.openai.max-attempts` | 10 | 1 |
| `embabel.agent.platform.llm-operations.data-binding.max-attempts` | 10 | 1 |
| `embabel.agent.platform.decisions.llm.max-attempts` | 5 | 1 |

These settings limit the configured retry templates. They do not limit tool conversation turns or compatibility fallback HTTP calls. The script submits each attempted outing once and stops after the first failed or non-usable result. The original nine-outing budget and subsequent three-outing approval counted submissions, not raw provider calls. All twelve submissions are recorded; further paid calls are on hold.

Researcher responses request at most 3000 output tokens; planner responses request at most 2500. Public page evidence is bounded to eight venues, up to two pages per venue and 6000 characters per page excerpt. These are response and evidence limits, not an aggregate token or spend cap. Raw provider usage and total cost were not collected. No cost estimate is inferred from timings or character counts.

## Validation and historical work

`mvn test package` runs model-free tests for typed actions, provider bounds, source validation, timed meals, cuisine coverage, safe preference recall, privacy, decision dispatch and destination-local dates. CI makes no provider calls.

The [Fort Lauderdale integration observation](results/refactor-fort-lauderdale-2026-10-14.md) preserves nine earlier submissions and three subsequently approved submissions. In the new batch, OpenAI selected a usable Cuban outline, local returned invalid travel timing and the Jev-selected request failed during discovery before calling Jev. Full three-engine acceptance remains blocked. Those twelve results predate the candidate/feedback changes in signed commits `636cbf5` and `82a5a66`. The latest batch was measured at `03f1bea0c4f828184f6cc8733cef611a61059a1f`; only one of its three outings was usable.

The earlier evidence/decision corrections passed 16 focused offline checks and a package gate under narrow independent review. The subsequent candidate/feedback changes independently passed 20 focused tests and an offline package gate. **Live behavior after those changes remains unverified.**

The three new response snapshots omit retrieved page excerpts and retain their source metadata, original character counts and SHA-256 digests. These observations are not controlled timing comparisons: public evidence and model outputs can differ between requests, and remote durations include network and provider work.

The preserved [earlier experimental article draft](docs/drafts/blog.md) and historical result files describe earlier implementations. They may refer to removed replay, targeted research loops or experimental adapters. The current app has no replay runner, bespoke retry research loop or native local CLEF model path. The article remains a draft and has not been published on Medium.

## License and provenance

Original Bayes work is [MIT licensed](LICENSE). The action flow adapts Apache-2.0 Embabel Tripper material, whose attribution and license are kept in [Third-party licenses](THIRD_PARTY_LICENSES.md). Embabel Agent and Dice are external dependencies with their own licenses.

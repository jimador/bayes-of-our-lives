# Bayes of Our Lives

Bayes is a one-day city guide built with [Embabel Agent 1.5.4-SNAPSHOT](https://github.com/embabel/embabel-agent), a JVM framework for building agents from actions and goals. It's based on [Tripper](https://github.com/embabel/tripper) and follows the same brief → ideas → researched places → plan flow. Once it has plans, it asks a typed question about the **whole itinerary**. That decision goes through a configurable `DecisionService`, so the same agent actions and plan types can use local deterministic rules, a decision model like TypeSafe Jev or a traditional LLM like OpenAI `gpt-4.1`.

The app retrieves mapped places, venue websites, Wikipedia search leads and base routes. OpenAI discovers relevant sights, researches up to five places and proposes up to two itineraries. Bayes maps the proposed names back to known places, checks timing and tagged closures and marks unsupported claims as open questions. If the decision comes back as `research`, Bayes allows one targeted official-page lookup, one updated research and plan pass and one more decision. After that it reports whatever gaps are left.

There's a [Medium-ready article draft](docs/drafts/blog.md) that walks through the design. It has not been published on Medium yet.

## Run the city guide

You need Java 21, Maven, `curl`, Embabel Agent `1.5.4-SNAPSHOT`, Dice `0.4.0` and an **existing runtime `OPENAI_API_KEY`** for research and planning. Research and planning always use OpenAI, even when local rules or Jev make the final decision. Jev also reads an existing runtime `TYPESAFE_API_KEY`. Keep credentials out of arguments, browser fields and repository settings.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/** and fill in the city, starting point, destination-local date and window, meal choices, group, interests, pace and any optional needs. Optional cuisine likes and dislikes accept common cuisine names such as `brazilian`, `thai` and `italian`, separated by commas. Use the separate dietary field for allergies or restrictions; cuisine fields reject that wording. The form asks for public-map consent and remote-model consent separately.

`POST /api/itinerary` accepts `city`, `startingLocation`, destination-local `date`, `startTime`,
`endTime`, `breakfast`, `coffee`, `earlyFoodDeclined`, `lunch`, `dinner`, `partySize`,
`childrenCount`, `interests`, `pace` (`relaxed`, `steady`, `busy`), `stepFreeAccess`,
`cuisineLikes`, `cuisineDislikes`, `dietaryOrAllergyNeeds`, `engine` (`local`, `jev`,
`openai`), `publicMapConsent`, and `remoteModelConsent`. Optional local preference recall uses
`profileId`, `rememberPreferences`, and `useRememberedPreferences`. Cuisine lists accept comma or
semicolon separators and simple aliases such as `sea food` and `cubano`; they are preferences,
not medical restrictions. For example:

```sh
curl -sS http://127.0.0.1:8080/api/itinerary -H 'Content-Type: application/json' -d '{
  "city":"Fort Lauderdale, Florida","startingLocation":"Downtown Fort Lauderdale",
  "date":"2030-10-15","startTime":"07:00","endTime":"14:00",
  "breakfast":true,"coffee":false,"earlyFoodDeclined":false,"lunch":true,"dinner":false,
  "partySize":2,"childrenCount":0,"interests":"art","pace":"steady",
  "stepFreeAccess":false,"cuisineLikes":"seafood, cuban","cuisineDislikes":"italian",
  "dietaryOrAllergyNeeds":"","engine":"local","publicMapConsent":true,"remoteModelConsent":true
}'
```

The model sees the city, date/window, group, interests, pace, cuisine preferences, access requirement, mapped places and research evidence. It does not see the exact starting point or allergy text. The exact starting point goes to public map and route services; liked cuisine search terms go to Photon. If you pick Jev or OpenAI for the decision, the itinerary alternatives are sent to that engine. If early-food choices are missing, Bayes asks about them before calling any provider. Answering or revising the form resubmits the request.

Dice captures confirmed interests and pace. **Remember preferences** saves those fields under a browser-generated profile ID in the ignored `.bayes-data/preferences.json` file, restricted to the owner on POSIX systems. A later outing can opt to use them. Meals, party, access and dietary needs always come from the current form. Exact allergy text stays out of map and model inputs, and food stops need direct confirmation. To clear saved preferences, delete the local file.

## Evidence and limits

Public lookup adapters use [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) and [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md) for places, [OSRM](https://project-osrm.org/docs/v5.24.0/api/) for base driving time, OSM-recorded HTTPS venue pages and Wikipedia search leads. Breakfast requests add a bounded Photon search for named breakfast restaurants or cafes near the starting point, then use a Nominatim lookup for those exact OSM IDs to obtain mapped cuisine and opening-hours tags. Restaurant searches can send up to two liked cuisine terms to Photon, plus a raw-bar alias for seafood. They do not send dislikes or allergy text. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Public services can go down or change their results. Venue-page requests validate public addresses, pin the checked IP with `curl --resolve`, verify the HTTPS hostname and disable proxies and redirects. External pages and model output are treated as untrusted data.

Bayes uses the geocoded destination timezone to check the requested window and flags daylight-saving crossings. It schedules each stop at the earliest known-open slot that fits mapped opening hours, base travel, meal windows and the outing end. If a proposed venue or order cannot fit, it tries a bounded set of mapped sights, food venues and lunch orders before returning up to two distinct alternatives. If no supported slot exists, the alternative stays invalid. Unknown opening hours never become proof of availability; even a mapped open slot asks for current confirmation. Tagged access, fees and cuisine stay provisional. For food stops, the decision input carries mapped cuisine tags, cited but unverified page clues, preference matches, breakfast service clues and the fit of the requested time against mapped hours. A cafe or coffee tag does not establish full breakfast, and open hours do not establish meal service. Missing admission, menus, child suitability, step-free details or food safety stay as open questions. OSRM does not supply live traffic.

Embabel's web, maps and browser tool-group roles existed in the tested API, but their MCP providers were not registered in this app's runtime. The named public adapters above supply the place and page evidence.

This repository does **not** include a native local CLEF model path. An earlier experimental adapter needed a Python bridge, a separately prepared Core ML variant and a checker. It was removed during cleanup. We have not verified that the specific CLEF 0.6B joint-decision model works through an ordinary chat-model configuration. Local deterministic rules are still available for the typed decision and don't need a decision-model key. Live research and planning still need OpenAI.

## Tests and measured observations

`mvn test package` runs model-free request, Dice memory, proposal hard-rule, food evidence, research, consent, timezone and decision-adapter tests. CI uses no API keys. A bounded [Fort Lauderdale live check](results/live-fort-lauderdale-check-2026-10-13.md) and [fixed decision replay](results/itinerary-replay-fort-lauderdale-2026-10-13.md) record separate observations from the earlier build without cuisine preferences. The live check used one request per engine and no warmups; the engines saw different changing public evidence. These files are functional observations, not controlled timing comparisons.

The [latest Fort Lauderdale browser and backend check](results/live-fort-lauderdale-check-2026-10-13-v4.md)
and [same-input decision replay](results/itinerary-replay-fort-lauderdale-2026-10-13-v4.md)
show the scheduling repair on one synthetic outing. The [previous cuisine-aware check](results/live-fort-lauderdale-check-2026-10-13-v3.md)
records the invalid early-sight plans that prompted it.

The numbers below are **historical small-sample observations**. They are not a performance ranking. The earlier Miami raw capture and result files were removed during cleanup and can be recovered from Git history.

For a fresh fixed-input replay, start Bayes with `--bays.replay.capture-file=.bayes-data/itinerary-replay.json`
and make one **synthetic, consented** live itinerary request. The ignored local capture holds the exact
`DecisionService` input and choice descriptions, so inspect it before sharing it. Do not put people's names, private addresses, allergy notes, credentials, or other private text in a replay request. The capture writer
skips capture when dietary notes or non-synthetic interests are present, without stopping the outing. Then run:

```sh
java -jar target/bays-0.1.0.jar --spring.main.web-application-type=none \
  --bays.replay.run-file=.bayes-data/itinerary-replay.json \
  --bays.replay.output-prefix=results/itinerary-replay-current --bays.replay.runs=2
```

The runner sends one immutable captured decision question to local rules, Jev and OpenAI in order,
with two measured repetitions per available engine by default. It skips an engine whose runtime key
is absent and records typed failures without retrying. It does not rediscover places, replan, or
change Dice memory. `results/itinerary-replay-current.{md,json}` contains the input hash, typed
answers, skips/failures and separate decision, classification and total times; it excludes the raw
input. Classification appends the selected alternative type to that same input. Remote times include
network and provider work, which cannot be isolated here. Review result files before publishing.
The historical observations below were collected on earlier builds and are not a performance ranking.

Four bounded full-app synthetic Miami checks were also run. Two initial calls succeeded (local 17.4 s, tentative unresolved B; Jev 20.0 s, still requesting research after one retry). An audit request failed with HTTP 502 after 56.9 s, and the exact failed stage was not captured. A later hardened local call took 21.2 s and returned tentative unresolved A. These times include changing public sources, several OpenAI calls, network and local agent work. The builds and evidence differed between runs, so treat the whole-request times as illustrative only.

## License and provenance

Original Bayes work is [MIT licensed](LICENSE). The action flow adapts Apache-2.0 Embabel Tripper material, and its attribution and full license text are kept in [Third-party licenses](THIRD_PARTY_LICENSES.md). Embabel Agent and Dice are external dependencies with their own licenses.

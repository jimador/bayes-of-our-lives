# Bayes of Our Lives

Bayes is a one-day city guide built with [Embabel Agent 1.5.4-SNAPSHOT](https://github.com/embabel/embabel-agent), a JVM framework for building agents from actions and goals. It's based on [Tripper](https://github.com/embabel/tripper) and follows the same brief → ideas → researched places → plan flow. Once it has plans, it asks a typed question about the **whole itinerary**. That decision goes through a configurable `DecisionService`, so the same agent actions and plan types can use local deterministic rules, a decision model like TypeSafe Jev or a traditional LLM like OpenAI `gpt-4.1`.

The app retrieves mapped places, venue websites, Wikipedia search leads and base routes. OpenAI discovers relevant sights, researches up to four places and proposes up to two itineraries. Bayes maps the proposed names back to known places, checks timing and tagged closures and marks unsupported claims as open questions. If the decision comes back as `research`, Bayes allows one targeted official-page lookup, one updated research and plan pass and one more decision. After that it reports whatever gaps are left.

There's a [Medium-ready article draft](docs/drafts/blog.md) that walks through the design. It has not been published on Medium yet.

## Run the city guide

You need Java 21, Maven, `curl`, Embabel Agent `1.5.4-SNAPSHOT`, Dice `0.4.0` and an **existing runtime `OPENAI_API_KEY`** for research and planning. Research and planning always use OpenAI, even when local rules or Jev make the final decision. Jev also reads an existing runtime `TYPESAFE_API_KEY`. Keep credentials out of arguments, browser fields and repository settings.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/** and fill in the city, starting point, destination-local date and window, meal choices, group, interests, pace and any optional needs. Optional cuisine likes and dislikes accept common cuisine names such as `brazilian`, `thai` and `italian`, separated by commas. Use the separate dietary field for allergies or restrictions; cuisine fields reject that wording. The form asks for public-map consent and remote-model consent separately.

The model sees the city, date/window, group, interests, pace, cuisine preferences, access requirement, mapped places and research evidence. It does not see the exact starting point or allergy text. The exact starting point goes to public map and route services. If you pick Jev or OpenAI for the decision, the itinerary alternatives are sent to that engine. If early-food choices are missing, Bayes asks about them before calling any provider. Answering or revising the form resubmits the request.

Dice captures confirmed interests and pace. **Remember preferences** saves those fields under a browser-generated profile ID in the ignored `.bayes-data/preferences.json` file, restricted to the owner on POSIX systems. A later outing can opt to use them. Meals, party, access and dietary needs always come from the current form. Exact allergy text stays out of map and model inputs, and food stops need direct confirmation. To clear saved preferences, delete the local file.

## Evidence and limits

Public lookup adapters use [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) and [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md) for places, [OSRM](https://project-osrm.org/docs/v5.24.0/api/) for base driving time, OSM-recorded HTTPS venue pages and Wikipedia search leads. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Public services can go down or change their results. Venue-page requests validate public addresses, pin the checked IP with `curl --resolve`, verify the HTTPS hostname and disable proxies and redirects. External pages and model output are treated as untrusted data.

Bayes uses the geocoded destination timezone to check the requested window and flags daylight-saving crossings. A tagged closure invalidates an alternative. Tagged opening hours, access, fees and cuisine stay provisional. For food stops, the decision input carries mapped cuisine tags, cited but unverified page clues, preference matches, breakfast service clues and the fit of the requested time against mapped hours. A cafe or coffee tag does not establish full breakfast, and open hours do not establish meal service. Missing admission, menus, child suitability, step-free details or food safety stay as open questions. OSRM does not supply live traffic.

Embabel's web, maps and browser tool-group roles existed in the tested API, but their MCP providers were not registered in this app's runtime. The named public adapters above supply the place and page evidence.

This repository does **not** include a native local CLEF model path. An earlier experimental adapter needed a Python bridge, a separately prepared Core ML variant and a checker. It was removed during cleanup. We have not verified that the specific CLEF 0.6B joint-decision model works through an ordinary chat-model configuration. Local deterministic rules are still available for the typed decision and don't need a decision-model key. Live research and planning still need OpenAI.

## Tests and measured observations

`mvn test package` runs model-free request, Dice memory, proposal hard-rule, food evidence, research, consent, timezone and decision-adapter tests. CI uses no API keys. A bounded [Fort Lauderdale live check](results/live-fort-lauderdale-check-2026-10-13.md) and [fixed decision replay](results/itinerary-replay-fort-lauderdale-2026-10-13.md) record separate observations from the earlier build without cuisine preferences. The live check used one request per engine and no warmups; the engines saw different changing public evidence. These files are functional observations, not controlled timing comparisons.

The numbers below are **historical small-sample observations**. They are not a performance ranking. The earlier Miami raw capture and result files were removed during cleanup and can be recovered from Git history.

For the decision-only comparison, one synthetic captured Miami itinerary was passed byte-for-byte to `BayesAgent.decideItinerary` for each engine. A was invalid because of mapped hours and B was unresolved. Local rules, Jev and OpenAI each ran one warmup and two measured calls, and typed outputs and input SHA-256 were checked.

| Decision engine | Answer | Measured decision calls |
| --- | --- | --- |
| Local rules | Tentative B | 0.264 ms, 0.193 ms |
| Jev `jev-1.13.0` | Research | 194 ms, 171 ms |
| OpenAI `gpt-4.1` | Research | 1132 ms, 982 ms |

Remote times include adapter, network, provider work and parsing. There is no separate provider-inference measurement. The retired CLEF adapter had one measured, no-warmup decision call of 2268 ms on a separately prepared local CPU variant and selected tentative B. The replay skipped fresh discovery, research and planning. These samples do not establish speed or quality differences.

Four bounded full-app synthetic Miami checks were also run. Two initial calls succeeded (local 17.4 s, tentative unresolved B; Jev 20.0 s, still requesting research after one retry). An audit request failed with HTTP 502 after 56.9 s, and the exact failed stage was not captured. A later hardened local call took 21.2 s and returned tentative unresolved A. These times include changing public sources, several OpenAI calls, network and local agent work. The builds and evidence differed between runs, so treat the whole-request times as illustrative only.

## License and provenance

Original Bayes work is [MIT licensed](LICENSE). The action flow adapts Apache-2.0 Embabel Tripper material, and its attribution and full license text are kept in [Third-party licenses](THIRD_PARTY_LICENSES.md). Embabel Agent and Dice are external dependencies with their own licenses.

# Bayes of Our Lives

Bayes is a one-day city guide built with [Embabel Agent 1.5.4-SNAPSHOT](https://github.com/embabel/embabel-agent). It ports [Tripper](https://github.com/embabel/tripper)'s LLM discovery → per-place research → plan proposal actions to Java, then adds an itinerary-level typed decision. A small engine selection changes that decision implementation among local Java rules, TypeSafe Jev, OpenAI `gpt-4.1`, and an optional local CLEF runtime. The same agent actions and plan types carry the work for every engine.

The LLM stages use independently retrieved public venue websites and Wikipedia search leads alongside mapped places and base routes. They do not treat snippets or OpenStreetMap tags as proof of today's hours, admission, access, menu, or availability. The LLM proposes venues and stop order; Java verifies that names were mapped and checks timing and hard closures. A `research` decision permits at most **one** targeted official-page retrieval, research update, replan, and redecision. If no new page evidence exists, it stops and reports the gap.

Read the [Medium-ready draft](docs/drafts/blog.md), [port smoke checks](results/tripper-port-smoke-2026-10-12.md), [fixed-context decision replay](results/itinerary-replay-2026-10-12.md), and [upstream reuse map](docs/tripper-reuse-map.md). The article is not published on Medium.

## Run the live city guide

Requires Java 21, Maven, `curl`, resolved Embabel Agent `1.5.4-SNAPSHOT` and Dice `0.4.0`, and an **existing runtime `OPENAI_API_KEY`** for Tripper-style LLM discovery, research, and planning. OpenAI calls are used for these stages even when local rules, JEV, or CLEF handles the itinerary decision. JEV additionally reads runtime `TYPESAFE_API_KEY`. Do not put keys in arguments, browser fields, or repository settings.

```sh
mvn test package
java -jar target/bays-0.1.0.jar
```

Open **http://127.0.0.1:8080/**. Enter city, origin, date/window, food choices, party size, interests, pace, and optional constraints. The form asks for public-map consent and separately lists the city, date, group, interests, pace, and access requirements sent to OpenAI for research/planning. The exact starting location and allergy text stay out of model prompts. JEV or OpenAI decision selection also sends itinerary alternatives to that engine. An early start without a breakfast or coffee answer returns a targeted question **before** any provider call. You can answer and resubmit, or edit other requirements and resubmit. A remaining `research` result identifies open questions rather than silently repeating calls.

Dice captures confirmed interests and pace. With **Remember preferences** consent, Dice's JSON proposition repository saves only those two fields in `.bayes-data/preferences.json`, scoped by a browser-generated profile ID. The app restricts this file to the owner on POSIX filesystems. A later outing can opt to **Use remembered preferences**. Without consent, the captured facts stay in memory for that request. Today's meals, party, access, and dietary needs always come from today's form. Exact allergy text stays out of map/model inputs; a boolean leads to direct venue-confirmation questions. Deleting the ignored local file clears preferences.

## Research and limits

The lower-level public tool adaptation uses [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) for sights/origin, [Photon](https://github.com/komoot/photon/blob/master/docs/api-v1.md) for cafés/restaurants, [OSRM](https://project-osrm.org/docs/v5.24.0/api/) for base driving time, OSM-recorded HTTPS venue websites, and bounded Wikipedia search leads. Places © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright). Public services have usage limits and can be unavailable. The app bounds mapped categories to three candidates, per-place LLM research to four candidates, and targeted retry to one distinct official page. Venue URLs must resolve only to public addresses; `curl --resolve` pins the checked IP while preserving TLS hostname verification, with proxies and redirects disabled. External pages and model output are treated as untrusted data. Open-Meteo's city timezone is used to reject past local windows and flag daylight-saving crossings; if the timezone is absent, it remains an open question. Mapped opening hours are parsed conservatively; a tagged closure invalidates an alternative. Tagged opening, access, fee, and cuisine facts remain provisional. Missing routes and facts become questions. OSRM has no live traffic; Bayes asks for a traffic check when timing slack is tight.

Embabel's `WEB`, `MAPS`, and `BROWSER_AUTOMATION` role constants exist in this API, but the current runtime registers only its `math` and `AppleScript` tool groups. Those three groups require MCP connection properties and providers that are not installed for this project. `java -jar target/bays-0.1.0.jar --bays.inspect-tools=true --bays.cli=true --bays.replay=true --bays.replay.engine=local --bays.replay.warmups=0 --bays.replay.runs=1` prints the actual registered groups. The Java port uses the named public services above for equivalent discovery and independent page research; it does not claim to run Tripper's original MCP tools. Brave images, weather MCP, Airbnb/lodging, and Google login are outside this one-day guide and are not required. See [reuse details](docs/tripper-reuse-map.md).

The optional CLEF **decision** adapter needs an external prepared model/checker and runtime `CLEF_MODEL_DIR`, `CLEF_CHECKER_PATH`, and `BAYS_CLEF_BRIDGE`; see [setup](clef/README.md). Its assets are not shipped here. This Python bridge is an experimental legacy adapter, pending a separate provider redesign; it is not a production runtime recommendation.

## Verify

`mvn test package` runs model-free request, Dice reload/isolation, proposal hard-rule, research citation, retry cap, consent, and adapter tests. CI needs no credentials. The [live smoke note](results/tripper-port-smoke-2026-10-12.md) documents four modest synthetic calls, including one failure, and whole-request timing limits. For byte-identical **decision** input across engines, build and run `benchmarks/run-itinerary-replay.sh local|jev|openai|clef`. It skips fresh discovery, research, and planning, defaults to one warmup and two measured calls, and verifies the input hash. See the [replay results](results/itinerary-replay-2026-10-12.md). The older [single-lead comparison](results/fixed-memory-comparison-2026-10-11.md) uses a separate CLI path.

## License and provenance

The [source map](docs/tripper-reuse-map.md) identifies the Tripper contracts and Java adaptations. [NOTICE](NOTICE.md) and the retained [Apache 2.0 license](licenses/TRIPPER-APACHE-2.0.txt) cover reused Tripper concepts/source in repository history. Original Bayes code is [MIT licensed](LICENSE). No source or wording from private writing drafts is included.

# Tripper decision demo

This is the runnable Tripper implementation described in [the article draft](../../docs/drafts/blog.md). It retains Tripper's original full-journey agent and adds a point-of-interest decision review plus a compact five-action city outing. Both call Embabel Agent `1.5.4-SNAPSHOT` decision and classification services. Set `tripper.decision.engine=local`, `jev`, or `openai` to change the decision implementation while keeping the agent actions.

## Run the lightweight outing

Use Java 21 and Maven. The local engine needs no model key. Tripper's existing OAuth configuration declares two properties even in CLI mode, so pass inert values for this run; these values do not enable login. Public geocoding, museum, forecast, and route services require network access and can change. Keep each manual run modest to respect the [public Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).

```sh
cd examples/tripper-decision-demo
mvn test package
GOOGLE_CLIENT_ID=unused GOOGLE_CLIENT_SECRET=unused java -jar target/tripper-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --tripper.outing.run=true \
  --tripper.decision.engine=local \
  '--tripper.outing.destination=Miami, Florida' \
  '--tripper.outing.origin=Miami, Florida' \
  --tripper.outing.simulated-traffic-delay-minutes=15
```

For a bounded remote comparison, change only `--tripper.decision.engine=jev` or `openai` and supply the existing runtime `TYPESAFE_API_KEY` or `OPENAI_API_KEY`, respectively. Do not place keys in arguments or files. The OpenAI baseline is `gpt-4.1-mini`. One outing requests at most three mapped museum leads, with one mixed decision request and one classification request per lead. `--tripper.dice.enabled=true` optionally starts and stops an app-owned Neo4j container for a fictional preference; Docker is required for that path. The default local outing needs no graph container.

The full journey agent remains in the source, but its separate research, lodging, image, and web integrations need their own setup. The CLI outing is the small end-to-end path to run first. Its output is a **research lead**, not a confirmed visit: mapped places, forecast weather, and OSRM base drive do not verify opening hours, admission, accessibility, or live traffic.

## Evidence and provenance

[Live outing observations](results/city-outing-live-2026-10-11.md) and [typed decision smoke](results/decision-smoke-2026-10-11.md) were measured in the source Tripper working branch, before this copy. They include run counts, timing boundaries, typed failures, and limitations. Build and tests were also checked from this copied directory; the dated live observations were not repeated after copying.

This example derives from [Embabel Tripper](https://github.com/embabel/tripper), base commit `002a08c`, under its [Apache License 2.0](LICENSE). It contains Tripper source and resources needed to retain the original agent and run the outing, plus the decision changes and tests. The surrounding Decision Desk Java example is separate and MIT licensed. The Tripper Git history, build output, local settings, images, and Docker deployment files are not included here.

# Live Miami web check — 2026-10-12

This is a bounded functional check of the real-city web API, not a reproducible timing or model-quality benchmark. The place and route providers can change their answers. Three requests used `Miami, Florida`, sights, 90 minutes, and the **fictional** `art-quiet` Dice profile. The model keys remained in the server environment. No real traveler data was used. There were no warmup runs.

The public lookup returned [Pérez Art Museum Miami](https://www.openstreetmap.org/way/484587327), [Patricia & Phillip Frost Art Museum](https://www.openstreetmap.org/way/112768020), and [Patricia and Phillip Frost Museum of Science](https://www.openstreetmap.org/way/319426548). The middle lead's 24-minute one-way base route plus the illustrative 45-minute visit allowance exceeded 90 minutes, so the agent deferred it before any model request. The other two had four-minute base routes. All three engines saw identical normalized decision-input SHA-256 values for the three leads, respectively:

```text
6633c842079ff8448c7a433b661365144a1570a6dd0013737e94845bb30fcc80
df6076d33029117e9589dec6059050a277398d391a19b91c2217ee33504ad798
a51f94709b68b3b1869c3a0d73edd807b4c667bdb911fecceac7da7b7924d822
```

| Engine | Attempts / complete results | Pérez answer | Frost Science answer | Selected research lead | Process elapsed |
| --- | ---: | --- | --- | --- | ---: |
| Java rules, offline control | 1 / 1 | `true`, `consider`, `possible`, `unknown` | `false`, `consider`, `possible`, `unknown` | Pérez Art Museum Miami | 2,467 ms |
| Jev `jev-1.13.0` | 2 / 1 | `true`, `consider`, `strong`, `unknown` | `false`, `consider`, `possible`, `unknown` | Pérez Art Museum Miami | 2,149 ms on complete request |
| OpenAI `gpt-4.1` | 1 / 1 | `true`, `prefer`, `strong`, `unknown` | `false`, `consider`, `possible`, `unknown` | Pérez Art Museum Miami | 5,424 ms |

Answers are proposition, choice, rating, classification in that order. One initial Jev web request produced an incomplete typed answer. The then-current API returned `MODEL_UNAVAILABLE` (HTTP 503), not a fallback recommendation; the final API distinguishes this as `DECISION_FAILED` (HTTP 502). A second bounded request completed. The measured process spans public lookup, Dice read, decision calls, and local ranking; it excludes browser work and includes different remote service and network costs. These counts do not establish reliability, relative speed, or general model quality.

The only live interest tag came from a literal word in each mapped venue name. Venue setting and ambience were unknown; opening hours, admission, accessibility, cuisine, dietary safety, and live traffic were not verified. These are **research leads**, not confirmed visit plans.

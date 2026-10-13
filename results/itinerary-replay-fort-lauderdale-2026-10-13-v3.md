# Typed itinerary decision replay

One immutable decision input was passed to each available engine. Classification adds only the selected itinerary type to that same input. No discovery, planning, Dice retrieval, or warmup. Remote durations include adapter, network, provider work, and parsing; inference is not isolated. Small illustrative samples do not establish a speed or quality ranking.

Input SHA-256: `6faecb8798faf30d1b385ba0103d5046c60c1dc7c8a4c99cc3d2206cdcfff881`. Repetitions per available engine: 2.

| Engine | Run | Status | Fully sourced | Next | Fit | Classification | Decision ms | Classification ms | Total ms | Note |
| --- | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |
| local | 1 | ok | false | research | weak | unknown | 5.810 | 0.111 | 8.866 |  |
| local | 2 | ok | false | research | weak | unknown | 0.613 | 0.024 | 0.691 |  |
| jev | 1 | ok | false | research | weak | unknown | 380.231 | 158.774 | 564.861 |  |
| jev | 2 | ok | false | research | weak | unknown | 150.154 | 136.018 | 286.368 |  |
| openai | 1 | ok | false | research | weak | unknown | 1098.759 | 844.735 | 1945.635 |  |
| openai | 2 | ok | false | research | weak | unknown | 1074.782 | 550.209 | 1625.291 |  |

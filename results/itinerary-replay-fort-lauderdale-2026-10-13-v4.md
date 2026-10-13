# Typed itinerary decision replay

One immutable decision input was passed to each available engine. Classification adds only the selected itinerary type to that same input. No discovery, planning, Dice retrieval, or warmup. Remote durations include adapter, network, provider work, and parsing; inference is not isolated. Small illustrative samples do not establish a speed or quality ranking.

Input SHA-256: `4934e6f11569da4c0e88483f781ac434e29ec5090d02505e453dd06e0970a6a0`. Repetitions per available engine: 2.

| Engine | Run | Status | Fully sourced | Next | Fit | Classification | Decision ms | Classification ms | Total ms | Note |
| --- | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |
| local | 1 | ok | false | a | possible | sight-and-meal | 4.847 | 0.067 | 7.381 |  |
| local | 2 | ok | false | a | possible | sight-and-meal | 0.786 | 0.028 | 0.866 |  |
| jev | 1 | ok | false | research | possible | unknown | 428.285 | 147.560 | 600.432 |  |
| jev | 2 | ok | false | research | possible | unknown | 150.896 | 135.040 | 286.053 |  |
| openai | 1 | ok | false | research | possible | unknown | 1088.677 | 597.147 | 1687.627 |  |
| openai | 2 | ok | false | research | possible | unknown | 970.362 | 669.723 | 1640.247 |  |

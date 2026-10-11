# CLEF 0.6B experimental local adapter

This directory contains a small JSON bridge and build helpers for [FluidInference/clef-text-0.6b-coreml](https://huggingface.co/FluidInference/clef-text-0.6b-coreml), pinned to revision `3c61a60954478ef385aa42d67d5764642dcc9135` (Apache-2.0). Its [license](LICENSE), bridge tests, local Core ML package transform, and a patch for the publisher's [FluidUse](https://github.com/FluidInference/FluidUse) checker are included. **Model weights, compiled packages, local environments, and binaries are not in Git.**

The untouched publisher Core ML package did not load on the test host. `repack_main.py 1024` derives a single-function L1024 package from it, checks semantic graph equality and byte-identical weight files, and writes local `provenance.json` with exact hashes. This is a **derived CPU workaround**, not the untouched publisher package or a production local model. The original and derived artifacts are not interchangeable in claims.

The one-shot `clef_bridge.py` reads one JSON object on stdin and returns one JSON object on stdout. It supports proposition, choice, ordered categorical rating, and classification questions with probabilities. The bridge limits input size and question count, uses a nonblocking local lock, and gives typed errors for missing artifacts, timeouts, and malformed output. Bays starts it with an outer 50-second deadline; failures stay typed and cannot turn a venue into an approved lead. The model does not perform ranking.

To run the serialization tests without downloading a model:

```sh
python3 -m unittest discover -s clef -p 'test_*.py' -v
```

To install the pinned model locally, use the Hugging Face CLI and Python 3.12 with `coremltools==9.0`; then build the pinned FluidUse checker with Swift. From `clef/`:

```sh
hf download FluidInference/clef-text-0.6b-coreml --revision 3c61a60954478ef385aa42d67d5764642dcc9135 --local-dir model
python3.12 -m venv .venv
.venv/bin/pip install coremltools==9.0
.venv/bin/python repack_main.py 1024
git clone https://github.com/FluidInference/FluidUse.git fluiduse-upstream
git -C fluiduse-upstream checkout 0230049c4e81f8de53ae1086b20cd9106c511105
git -C fluiduse-upstream apply ../runtime.patch
(cd fluiduse-upstream && swift build -c release -debug-info-format none --product ClefTextCheck)
```

The build script expects the downloaded model files in `clef/model/` and creates `clef/model-main-1024/`, both ignored by Git. `CLEF_MODEL_DIR` and `CLEF_CHECKER_PATH` can point to equivalent local artifacts elsewhere. Run Bays with `--demo.engine=clef --bays.profile=art-quiet` after installation. This invokes the real Embabel agent and bridge; without artifacts it returns typed `UNAVAILABLE` outcomes and no automatically approved lead. The same [fixed input capture](../benchmarks/fixed-art-quiet.json) used for the cloud runs records the synthetic Dice context and candidate facts.

On the original test host, the derived decoder used CPU despite GPU/ANE being requested; the child process reached about 8.6 GiB peak RSS in isolated feasibility checks. A single older fixture took about 10.45 seconds wall including load. These are environment-specific diagnostics, not Bays comparison results. The original package's failure and the derived package's behavior are separate facts.

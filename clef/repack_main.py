"""Diagnostic only: preserve L256 graph/weights as a conventional one-function ML Program.

The published package in model/ is unchanged. This derived package is not a
publisher artifact and must not be reported as one.
"""
from pathlib import Path
import hashlib
import json
import shutil
import sys
import coremltools as ct
from google.protobuf.json_format import MessageToDict


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


source_root = Path("model")
bucket = int(sys.argv[1]) if len(sys.argv) > 1 else 256
function = f"L{bucket}"
target_root = Path(f"model-main-{bucket}")
target_root.mkdir(exist_ok=True)
shutil.rmtree(target_root / "compiled", ignore_errors=True)
provenance = {
    "source_repo": "FluidInference/clef-text-0.6b-coreml",
    "source_revision": "3c61a60954478ef385aa42d67d5764642dcc9135",
    "license": "Apache-2.0",
    "variant": f"local-single-function-L{bucket}",
    "tool": f"coremltools {ct.__version__}",
    "files": {},
}
for name in ("Decoder", "Head"):
    src = source_root / f"{name}.mlpackage"
    dest = target_root / f"{name}.mlpackage"
    source_spec = ct.models.utils.load_spec(str(src))
    source_graph = MessageToDict(source_spec.mlProgram.functions[function])
    function_description = next(item for item in source_spec.description.functions if item.name == function)
    source_spec.description.input.extend(function_description.input)
    source_spec.description.output.extend(function_description.output)
    source_spec.description.functions.clear()
    source_spec.description.defaultFunctionName = ""
    for key in list(source_spec.mlProgram.functions.keys()):
        if key != function:
            del source_spec.mlProgram.functions[key]
    source_spec.mlProgram.functions["main"].CopyFrom(source_spec.mlProgram.functions[function])
    del source_spec.mlProgram.functions[function]
    weights_dir = src / "Data/com.apple.CoreML/weights"
    ct.models.MLModel(source_spec, weights_dir=str(weights_dir), skip_model_load=True).save(str(dest))
    target_spec = ct.models.utils.load_spec(str(dest))
    target_graph = MessageToDict(target_spec.mlProgram.functions["main"])
    src_weights = weights_dir / "weight.bin"
    dst_weights = dest / "Data/com.apple.CoreML/weights/weight.bin"
    assert source_graph == target_graph, f"{name} graph changed"
    assert sha256(src_weights) == sha256(dst_weights), f"{name} weights changed"
    provenance["files"][name] = {
        "source_spec_sha256": sha256(src / "Data/com.apple.CoreML/model.mlmodel"),
        "derived_spec_sha256": sha256(dest / "Data/com.apple.CoreML/model.mlmodel"),
        "weights_sha256": sha256(src_weights),
        "graph_semantically_equal": True,
    }
    print(name, "graph_equal", source_graph == target_graph, "weight_sha_equal", sha256(src_weights) == sha256(dst_weights),
          "functions", list(target_spec.mlProgram.functions.keys()), "description_functions", len(target_spec.description.functions),
          "input_count", len(target_spec.description.input), flush=True)
shutil.copyfile(source_root / "LICENSE", target_root / "LICENSE")
for filename in ("config.json", "tokenizer.json", "embeddings.f16"):
    link = target_root / filename
    if not link.exists():
        link.symlink_to(Path("..") / source_root / filename)
provenance["files"]["embeddings.f16"] = sha256(source_root / "embeddings.f16")
provenance["files"]["tokenizer.json"] = sha256(source_root / "tokenizer.json")
(target_root / "provenance.json").write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n")

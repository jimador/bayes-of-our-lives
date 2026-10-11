#!/usr/bin/env python3
"""One-shot JSON bridge for the experimental CLEF Core ML decision model.

Reads one JSON request from stdin; writes one JSON response to stdout. It uses
the publisher's ClefTextCheck binary with the locally derived L1024 package.
The original Hugging Face package remains unchanged and is not loadable here.
"""
from __future__ import annotations

import fcntl
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


ROOT = Path(__file__).resolve().parent
ID = re.compile(r"^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
MAX_REQUEST_BYTES = 16_384
MAX_STATE_BYTES = 4_096
MAX_QUESTIONS = 16
MAX_OPTIONS = 96
MAX_STRING_BYTES = 512
TIMEOUT_SECONDS = 45


class BridgeError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def _string(value, field: str, limit: int = MAX_STRING_BYTES) -> str:
    if not isinstance(value, str) or not value.strip():
        raise BridgeError("invalid_request", f"{field} must be a nonempty string")
    if len(value.encode("utf-8")) > limit:
        raise BridgeError("invalid_request", f"{field} exceeds {limit} UTF-8 bytes")
    return value


def _id(value, field: str) -> str:
    if not isinstance(value, str) or not ID.fullmatch(value):
        raise BridgeError("invalid_request", f"{field} must match {ID.pattern}")
    return value


def _options(value, field: str, maximum: int) -> list[dict]:
    if not isinstance(value, list) or not 2 <= len(value) <= maximum:
        raise BridgeError("invalid_request", f"{field} needs 2 to {maximum} entries")
    options = []
    seen = set()
    for index, option in enumerate(value):
        if not isinstance(option, dict):
            raise BridgeError("invalid_request", f"{field}[{index}] must be an object")
        option_id = _id(option.get("id"), f"{field}[{index}].id")
        if option_id in seen:
            raise BridgeError("invalid_request", f"duplicate option id {option_id}")
        seen.add(option_id)
        options.append({"id": option_id, "description": _string(option.get("description"), f"{field}[{index}].description")})
    return options


def compile_request(value: object) -> tuple[dict, list[dict]]:
    """Validate the Java-facing schema and create the publisher checker schema."""
    if not isinstance(value, dict):
        raise BridgeError("invalid_request", "request must be an object")
    state = _string(value.get("state"), "state", MAX_STATE_BYTES)
    raw_questions = value.get("questions")
    if not isinstance(raw_questions, list) or not 1 <= len(raw_questions) <= MAX_QUESTIONS:
        raise BridgeError("invalid_request", f"questions needs 1 to {MAX_QUESTIONS} entries")
    questions = []
    checker_questions = []
    seen = set()
    total_options = 0
    for index, raw in enumerate(raw_questions):
        if not isinstance(raw, dict):
            raise BridgeError("invalid_request", f"questions[{index}] must be an object")
        qid = _id(raw.get("id"), f"questions[{index}].id")
        if qid in seen:
            raise BridgeError("invalid_request", f"duplicate question id {qid}")
        seen.add(qid)
        kind = raw.get("kind")
        if kind not in ("proposition", "choice", "rating", "classification"):
            raise BridgeError("invalid_request", f"unsupported question kind for {qid}")
        instructions = _string(raw.get("instructions"), f"questions[{index}].instructions")
        if kind == "proposition":
            if "options" in raw or "levels" in raw:
                raise BridgeError("invalid_request", f"{qid} proposition has implicit true/false options")
            wire = {"type": "noul", "instructions": instructions}
            options = [{"id": "true"}, {"id": "false"}]
        elif kind == "rating":
            options = _options(raw.get("levels"), f"questions[{index}].levels", 16)
            wire = {"type": "score", "instructions": instructions,
                    "criteria": [item["description"] for item in options]}
        else:
            options = _options(raw.get("options"), f"questions[{index}].options", 32)
            wire = {"type": "choice", "instructions": instructions,
                    "criteria": {item["id"]: item["description"] for item in options}}
        total_options += len(options)
        if total_options > MAX_OPTIONS:
            raise BridgeError("invalid_request", f"all questions together exceed {MAX_OPTIONS} options")
        questions.append({"id": qid, "kind": kind, "options": options})
        checker_questions.append([qid, wire])
    return {"state": state, "questions": checker_questions}, questions


def decode_prediction(raw: object, questions: list[dict]) -> dict:
    """Reject missing, malformed, or nonprobabilistic model output."""
    if not isinstance(raw, dict) or not isinstance(raw.get("answers"), dict):
        raise BridgeError("invalid_output", "checker returned no answers object")
    by_id = raw["answers"]
    if set(by_id) != {q["id"] for q in questions}:
        raise BridgeError("invalid_output", "checker answer ids differ from request")
    tokens = raw.get("tokens")
    if not isinstance(tokens, int) or tokens < 1:
        raise BridgeError("invalid_output", "checker returned invalid token count")
    if tokens >= 1024:
        raise BridgeError("input_too_long", "request filled the 1024-token bucket and may be truncated")
    latency = raw.get("ms")
    if not isinstance(latency, (int, float)) or not math.isfinite(latency) or latency < 0:
        raise BridgeError("invalid_output", "checker returned invalid latency")
    answers = []
    for question in questions:
        qid, kind = question["id"], question["kind"]
        wire = by_id[qid]
        if not isinstance(wire, dict):
            raise BridgeError("invalid_output", f"{qid} probabilities must be an object")
        if kind == "rating":
            expected_keys = {str(i) for i in range(len(question["options"]))}
            mapping = {str(i): option["id"] for i, option in enumerate(question["options"])}
        else:
            expected_keys = {option["id"] for option in question["options"]}
            mapping = {key: key for key in expected_keys}
        if set(wire) != expected_keys:
            raise BridgeError("invalid_output", f"{qid} option ids differ from request")
        probabilities = {}
        for key, value in wire.items():
            if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or not 0 <= value <= 1:
                raise BridgeError("invalid_output", f"{qid} has invalid probability")
            probabilities[mapping[key]] = float(value)
        if not 0.98 <= sum(probabilities.values()) <= 1.02:
            raise BridgeError("invalid_output", f"{qid} probabilities do not sum to one")
        selected = max(probabilities, key=probabilities.get)
        answer = {"id": qid, "kind": kind, "selected_id": selected,
                  "confidence": probabilities[selected], "probabilities": probabilities}
        if kind == "rating":
            answer["expected_index"] = sum(i * probabilities[option["id"]] for i, option in enumerate(question["options"]))
        answers.append(answer)
    return {"input_tokens": tokens, "inference_ms": float(latency), "answers": answers}


def infer(request: object) -> dict:
    wire, questions = compile_request(request)
    bundle = Path(os.environ.get("CLEF_MODEL_DIR", ROOT / "model-main-1024"))
    checker = Path(os.environ.get("CLEF_CHECKER_PATH", ROOT / "fluiduse-upstream/.build/release/ClefTextCheck"))
    if not checker.is_file() or not (bundle / "provenance.json").is_file():
        raise BridgeError("missing_artifact", "derived model or checker is not installed; see clef/README.md")
    provenance = json.loads((bundle / "provenance.json").read_text())
    if provenance.get("source_revision") != "3c61a60954478ef385aa42d67d5764642dcc9135" or provenance.get("variant") != "local-single-function-L1024":
        raise BridgeError("missing_artifact", "model provenance does not match the tested variant")
    temp_root = ROOT / "coreml-tmp"
    temp_root.mkdir(exist_ok=True)
    lock_path = ROOT / ".clef_bridge.lock"
    with lock_path.open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise BridgeError("busy", "another CLEF inference is running") from exc
        with tempfile.TemporaryDirectory(dir=temp_root, prefix="clef-request-") as temporary:
            input_path = Path(temporary) / "request.jsonl"
            input_path.write_text(json.dumps(wire, ensure_ascii=False, separators=(",", ":")) + "\n")
            env = os.environ.copy()
            env.update({"CLEF_SINGLE_MAIN": "1", "CLEF_COMPUTE": "cpu", "TMPDIR": str(temp_root)})
            started = time.perf_counter()
            try:
                completed = subprocess.run([str(checker), str(bundle), str(input_path)], env=env,
                                           capture_output=True, text=True, timeout=TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired as exc:
                raise BridgeError("timeout", f"CLEF exceeded {TIMEOUT_SECONDS} seconds") from exc
            wall_ms = (time.perf_counter() - started) * 1000
            if completed.returncode != 0:
                raise BridgeError("inference_failed", f"Core ML checker exited with status {completed.returncode}")
            lines = completed.stdout.splitlines()
            if len(lines) != 1:
                raise BridgeError("invalid_output", "checker must return exactly one JSON line")
            try:
                raw = json.loads(lines[0])
            except json.JSONDecodeError as exc:
                raise BridgeError("invalid_output", "checker returned malformed JSON") from exc
            decoded = decode_prediction(raw, questions)
    return {"ok": True, "experimental": True,
            "model": {"source_repo": provenance["source_repo"], "source_revision": provenance["source_revision"],
                      "variant": provenance["variant"], "license": provenance["license"],
                      "requested_compute": "cpu",
                      "source_decoder_spec_sha256": provenance["files"]["Decoder"]["source_spec_sha256"],
                      "source_head_spec_sha256": provenance["files"]["Head"]["source_spec_sha256"],
                      "derived_decoder_spec_sha256": provenance["files"]["Decoder"]["derived_spec_sha256"],
                      "derived_head_spec_sha256": provenance["files"]["Head"]["derived_spec_sha256"],
                      "decoder_weights_sha256": provenance["files"]["Decoder"]["weights_sha256"],
                      "head_weights_sha256": provenance["files"]["Head"]["weights_sha256"]},
            "wall_ms": wall_ms, **decoded}


def main() -> int:
    try:
        data = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
        if len(data) > MAX_REQUEST_BYTES:
            raise BridgeError("invalid_request", f"request exceeds {MAX_REQUEST_BYTES} bytes")
        try:
            request = json.loads(data)
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise BridgeError("invalid_request", "stdin must contain one JSON object") from exc
        result = infer(request)
        status = 0
    except BridgeError as exc:
        result = {"ok": False, "error": {"code": exc.code, "message": exc.message}}
        status = 1
    except Exception:
        result = {"ok": False, "error": {"code": "bridge_failure", "message": "unexpected local bridge error"}}
        status = 1
    print(json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(",", ":")))
    return status


if __name__ == "__main__":
    sys.exit(main())

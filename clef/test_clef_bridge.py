import json
from pathlib import Path
import subprocess
import sys
import os
import tempfile
import unittest
from unittest import mock

from clef_bridge import BridgeError, compile_request, decode_prediction, infer


ROOT = Path(__file__).parent


def request():
    return {
        "state": "A public museum in Miami has a mapped location, one source, and dates that overlap the trip.",
        "questions": [
            {"id": "usable", "kind": "proposition", "instructions": "Is it usable?"},
            {"id": "next", "kind": "choice", "instructions": "What next?", "options": [
                {"id": "prioritize", "description": "All research fields are present"},
                {"id": "review", "description": "Research is missing"}]},
            {"id": "effort", "kind": "rating", "instructions": "How much effort?", "levels": [
                {"id": "easy", "description": "Easy"}, {"id": "hard", "description": "Hard"}]},
            {"id": "setting", "kind": "classification", "instructions": "What setting?", "options": [
                {"id": "indoor", "description": "Museum"}, {"id": "outdoor", "description": "Park"}]},
        ],
    }


class BridgeSerializationTests(unittest.TestCase):
    def test_compiles_all_four_families_and_maps_rating_indices(self):
        wire, questions = compile_request(request())
        self.assertEqual([item[1]["type"] for item in wire["questions"]], ["noul", "choice", "score", "choice"])
        self.assertEqual(wire["questions"][2][1]["criteria"], ["Easy", "Hard"])
        raw = {"tokens": 170, "ms": 42.5, "answers": {
            "usable": {"true": 0.8, "false": 0.2},
            "next": {"prioritize": 0.7, "review": 0.3},
            "effort": {"0": 0.25, "1": 0.75},
            "setting": {"indoor": 0.9, "outdoor": 0.1}}}
        result = decode_prediction(raw, questions)
        self.assertEqual([a["selected_id"] for a in result["answers"]], ["true", "prioritize", "hard", "indoor"])
        self.assertEqual(result["answers"][2]["probabilities"], {"easy": 0.25, "hard": 0.75})
        self.assertAlmostEqual(result["answers"][2]["expected_index"], 0.75)

    def test_rejects_unsupported_ranking_and_duplicate_ids(self):
        bad = request()
        bad["questions"][0]["kind"] = "ranking"
        with self.assertRaisesRegex(BridgeError, "unsupported question kind"):
            compile_request(bad)
        bad = request()
        bad["questions"][1]["id"] = "usable"
        with self.assertRaisesRegex(BridgeError, "duplicate question id"):
            compile_request(bad)

    def test_rejects_missing_probabilities_and_possible_truncation(self):
        _, questions = compile_request(request())
        with self.assertRaisesRegex(BridgeError, "checker answer ids"):
            decode_prediction({"tokens": 100, "ms": 1.0, "answers": {"usable": {"true": 1.0, "false": 0.0}}}, questions)
        with self.assertRaisesRegex(BridgeError, "1024-token bucket"):
            decode_prediction({"tokens": 1024, "ms": 1.0, "answers": {q["id"]: {} for q in questions}}, questions)

    def test_rejects_overlarge_state_and_bad_probability(self):
        bad = request()
        bad["state"] = "x" * 4097
        with self.assertRaisesRegex(BridgeError, "state exceeds"):
            compile_request(bad)
        _, questions = compile_request(request())
        raw = {"tokens": 100, "ms": 1.0, "answers": {
            "usable": {"true": float("nan"), "false": 0.0},
            "next": {"prioritize": 0.7, "review": 0.3},
            "effort": {"0": 0.5, "1": 0.5},
            "setting": {"indoor": 0.5, "outdoor": 0.5}}}
        with self.assertRaisesRegex(BridgeError, "invalid probability"):
            decode_prediction(raw, questions)

    def test_cli_returns_typed_failure_without_loading_model(self):
        process = subprocess.run([sys.executable, str(ROOT / "clef_bridge.py")], input=b'{"state":"x","questions":[{"id":"rank","kind":"ranking","instructions":"Rank"}]}', capture_output=True)
        self.assertEqual(process.returncode, 1)
        payload = json.loads(process.stdout)
        self.assertEqual(payload["error"]["code"], "invalid_request")
        self.assertFalse(payload["ok"])

    def test_checker_timeout_is_typed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / "model-main-1024"
            bundle.mkdir()
            (bundle / "provenance.json").write_text(json.dumps({
                "source_revision": "3c61a60954478ef385aa42d67d5764642dcc9135",
                "variant": "local-single-function-L1024"}))
            checker = root / "ClefTextCheck"
            checker.touch()
            with mock.patch.dict(os.environ, {"CLEF_MODEL_DIR": str(bundle), "CLEF_CHECKER_PATH": str(checker)}):
                with mock.patch("clef_bridge.subprocess.run", side_effect=subprocess.TimeoutExpired("ClefTextCheck", 45)):
                    with self.assertRaises(BridgeError) as caught:
                        infer(request())
        self.assertEqual(caught.exception.code, "timeout")


if __name__ == "__main__":
    unittest.main()

"""A dependency edit cannot silently retain its previous licensing review."""
import copy
import json
from pathlib import Path
import unittest
from tools.check_license_inventory import validate


class LicenseReviewTests(unittest.TestCase):
    def test_current_review_and_tampered_records(self):
        root = Path(__file__).resolve().parent.parent
        review = json.loads((root / 'docs/operations/third-party-review.json').read_text())
        validate(root, review)
        for key in ('dependencyInputs', 'npmComponents', 'binaryReleaseCleared'):
            changed = copy.deepcopy(review)
            changed[key] = True if key == 'binaryReleaseCleared' else {}
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(root, changed)

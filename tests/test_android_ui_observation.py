import unittest
from tools.test_android_process_recovery import read_complete_ui_tree

class CompleteUiObservationTest(unittest.TestCase):
    def test_partial_xml_is_reobserved_instead_of_accepted(self):
        reads = iter(['<hierarchy><node text="truncated', '<hierarchy><node text="complete"/></hierarchy>'])
        dumps = []
        result = read_complete_ui_tree(lambda: dumps.append(True), lambda: next(reads))
        self.assertEqual([node.get('text') for node in result], ['complete'])
        self.assertEqual(len(dumps), 2)

    def test_persistent_truncation_fails_after_three_reads(self):
        dumps = []
        with self.assertRaisesRegex(AssertionError, 'incomplete XML'):
            read_complete_ui_tree(lambda: dumps.append(True), lambda: '<hierarchy>')
        self.assertEqual(len(dumps), 3)

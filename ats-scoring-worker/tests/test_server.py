import unittest

from app.server import validate


class ValidationTest(unittest.TestCase):
    def test_accepts_exactly_the_two_supported_documents(self):
        job, documents = validate({
            "jobDescription": "Java backend role",
            "documents": [
                {"id": "current", "text": "Java developer"},
                {"id": "generated", "text": "Java backend developer"},
            ],
        })
        self.assertEqual("Java backend role", job)
        self.assertEqual(["current", "generated"], [value["id"] for value in documents])

    def test_rejects_duplicate_document_ids(self):
        with self.assertRaisesRegex(ValueError, "unique current and generated"):
            validate({
                "jobDescription": "Java backend role",
                "documents": [
                    {"id": "current", "text": "one"},
                    {"id": "current", "text": "two"},
                ],
            })


if __name__ == "__main__":
    unittest.main()

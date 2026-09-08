import unittest

from app.configuration import Settings
from app.worker import RequestProblem, canonical_url, normalize_record, scrape_and_normalize, validate_request


SETTINGS = Settings("x" * 32, frozenset({"indeed"}), 32768, 2, 100, 120, 1)


class FakeFrame:
    def __init__(self, rows): self.rows = rows
    def to_dict(self, orient):
        assert orient == "records"
        return self.rows


class WorkerTests(unittest.TestCase):
    def test_policy_rejects_unapproved_site_and_unknown_field(self):
        with self.assertRaises(RequestProblem):
            validate_request({"requestId": "run-1", "sites": ["linkedin"], "query": "java"}, SETTINGS)
        with self.assertRaises(RequestProblem):
            validate_request({"requestId": "run-1", "sites": ["indeed"], "query": "java", "proxies": ["x"]}, SETTINGS)

    def test_normalizes_stable_identity_and_strips_tracking(self):
        job = normalize_record({
            "site": "indeed", "id": "in-123", "title": " Engineer ", "company": "Example",
            "job_url": "https://example.com/jobs/123?utm_source=x&keep=yes", "job_url_direct": None,
            "location": "Bengaluru, India", "description": "Build systems", "job_type": "fulltime",
            "is_remote": True, "min_amount": 10, "max_amount": 20, "currency": "inr", "interval": "yearly",
            "date_posted": "2026-08-29",
        })
        self.assertEqual("in-123", job["externalId"])
        self.assertEqual("https://example.com/jobs/123?keep=yes", job["sourceUrl"])
        self.assertEqual("FULL_TIME", job["employmentType"])
        self.assertEqual("REMOTE", job["workplaceType"])
        self.assertEqual("INR", job["salaryCurrency"])

    def test_url_identity_fallback_is_deterministic(self):
        record = {"site": "indeed", "title": "Engineer", "company": "Example", "job_url": "https://example.com/jobs/1"}
        self.assertEqual(normalize_record(record)["externalId"], normalize_record(record)["externalId"])
        self.assertTrue(normalize_record(record)["externalId"].startswith("url-"))

    def test_scrape_contract_maps_fixture_and_never_passes_proxies(self):
        captured = {}
        def scrape(**kwargs):
            captured.update(kwargs)
            return FakeFrame([{"site": "indeed", "id": "in-1", "title": "Engineer", "company": "Example", "job_url": "https://example.com/1"}])
        request = validate_request({"requestId": "run-1", "sites": ["indeed"], "query": "java", "resultsWanted": 5}, SETTINGS)
        result = scrape_and_normalize(request, scrape)
        self.assertEqual("SUCCEEDED", result["status"])
        self.assertEqual(1, len(result["jobs"]))
        self.assertIsNone(captured["proxies"])
        self.assertFalse(captured["linkedin_fetch_description"])

    def test_linkedin_requests_fetch_job_descriptions(self):
        captured = {}
        def scrape(**kwargs):
            captured.update(kwargs)
            return FakeFrame([{"site": "linkedin", "id": "li-1", "title": "Engineer", "company": "Example", "job_url": "https://example.com/1"}])
        settings = Settings("x" * 32, frozenset({"linkedin"}), 32768, 2, 100, 120, 1)
        request = validate_request({"requestId": "run-2", "sites": ["linkedin"], "query": "java"}, settings)

        scrape_and_normalize(request, scrape)

        self.assertTrue(captured["linkedin_fetch_description"])
        self.assertIsNone(captured["proxies"])

    def test_rejects_credentials_and_unsafe_url_schemes(self):
        with self.assertRaises(RequestProblem): canonical_url("file:///etc/passwd")
        with self.assertRaises(RequestProblem): canonical_url("https://user:pass@example.com/job")


if __name__ == "__main__": unittest.main()

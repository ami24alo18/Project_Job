from __future__ import annotations

import os
from dataclasses import dataclass


KNOWN_SITES = frozenset({"indeed", "linkedin", "zip_recruiter", "glassdoor", "google", "bayt", "naukri", "bdjobs"})


def _positive_int(name: str, default: int, maximum: int) -> int:
    raw = os.getenv(name, str(default))
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be an integer") from exc
    if value < 1 or value > maximum:
        raise RuntimeError(f"{name} must be between 1 and {maximum}")
    return value


@dataclass(frozen=True)
class Settings:
    token: str
    allowed_sites: frozenset[str]
    maximum_body_bytes: int
    maximum_sites: int
    maximum_results_per_site: int
    request_timeout_seconds: int
    maximum_concurrent_requests: int

    @staticmethod
    def from_environment() -> "Settings":
        token = os.getenv("JOBSPY_WORKER_TOKEN", "").strip()
        if len(token) < 24:
            raise RuntimeError("JOBSPY_WORKER_TOKEN must contain at least 24 characters")
        requested = {item.strip().lower() for item in os.getenv("JOBSPY_ALLOWED_SITES", "").split(",") if item.strip()}
        unknown = requested - KNOWN_SITES
        if unknown:
            raise RuntimeError("JOBSPY_ALLOWED_SITES contains an unsupported site")
        return Settings(
            token=token,
            allowed_sites=frozenset(requested),
            maximum_body_bytes=_positive_int("JOBSPY_MAX_BODY_BYTES", 32768, 262144),
            maximum_sites=_positive_int("JOBSPY_MAX_SITES_PER_REQUEST", 2, 4),
            maximum_results_per_site=_positive_int("JOBSPY_MAX_RESULTS_PER_SITE", 100, 250),
            request_timeout_seconds=_positive_int("JOBSPY_REQUEST_TIMEOUT_SECONDS", 120, 300),
            maximum_concurrent_requests=_positive_int("JOBSPY_MAX_CONCURRENT_REQUESTS", 1, 4),
        )

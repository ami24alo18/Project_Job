from __future__ import annotations

import hashlib
import math
import multiprocessing
import queue
import re
from datetime import date, datetime, timezone
from typing import Any, Callable
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from .configuration import Settings


TRACKING_KEYS = frozenset({"ref", "refid", "trk", "trackingid", "source"})
EMPLOYMENT = {
    "fulltime": "FULL_TIME", "parttime": "PART_TIME", "contract": "CONTRACT",
    "contractor": "CONTRACT", "temporary": "TEMPORARY", "internship": "INTERNSHIP",
}
INTERVAL = {"yearly": "YEAR", "monthly": "MONTH", "weekly": "WEEK", "daily": "DAY", "hourly": "HOUR"}


class RequestProblem(ValueError):
    pass


class ScrapeTimeout(RuntimeError):
    pass


def validate_request(payload: Any, settings: Settings) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise RequestProblem("The request body must be a JSON object")
    allowed = {"requestId", "sites", "query", "googleSearchTerm", "location", "country", "resultsWanted", "hoursOld", "remoteOnly", "jobType"}
    if set(payload) - allowed:
        raise RequestProblem("The request contains unsupported fields")
    request_id = _required_text(payload.get("requestId"), "requestId", 160, r"[A-Za-z0-9][A-Za-z0-9._:-]*")
    query = _required_text(payload.get("query"), "query", 500)
    sites = payload.get("sites")
    if not isinstance(sites, list) or not sites or len(sites) > settings.maximum_sites:
        raise RequestProblem(f"sites must contain between 1 and {settings.maximum_sites} entries")
    normalized_sites = []
    for site in sites:
        value = _required_text(site, "site", 40).lower()
        if value not in settings.allowed_sites:
            raise RequestProblem(f"Site '{value}' is not enabled by deployment policy")
        if value not in normalized_sites:
            normalized_sites.append(value)
    results = payload.get("resultsWanted", min(50, settings.maximum_results_per_site))
    if not isinstance(results, int) or isinstance(results, bool) or results < 1 or results > settings.maximum_results_per_site:
        raise RequestProblem(f"resultsWanted must be between 1 and {settings.maximum_results_per_site}")
    hours = payload.get("hoursOld")
    if hours is not None and (not isinstance(hours, int) or isinstance(hours, bool) or hours < 1 or hours > 8760):
        raise RequestProblem("hoursOld must be between 1 and 8760")
    remote = payload.get("remoteOnly", False)
    if not isinstance(remote, bool):
        raise RequestProblem("remoteOnly must be a boolean")
    return {
        "requestId": request_id,
        "sites": normalized_sites,
        "query": query,
        "googleSearchTerm": _optional_text(payload.get("googleSearchTerm"), 500),
        "location": _optional_text(payload.get("location"), 300),
        "country": (_optional_text(payload.get("country"), 80) or "usa").lower(),
        "resultsWanted": results,
        "hoursOld": hours,
        "remoteOnly": remote,
        "jobType": _optional_text(payload.get("jobType"), 30),
    }


def scrape_and_normalize(request: dict[str, Any], scrape: Callable[..., Any] | None = None) -> dict[str, Any]:
    if scrape is None:
        from jobspy import scrape_jobs
        scrape = scrape_jobs
    started = datetime.now(timezone.utc)
    frame = scrape(
        site_name=request["sites"], search_term=request["query"],
        google_search_term=request["googleSearchTerm"], location=request["location"],
        results_wanted=request["resultsWanted"], hours_old=request["hoursOld"],
        is_remote=request["remoteOnly"], job_type=request["jobType"],
        country_indeed=request["country"], description_format="markdown",
        linkedin_fetch_description="linkedin" in request["sites"], proxies=None, verbose=0,
    )
    records = frame.to_dict(orient="records") if hasattr(frame, "to_dict") else list(frame)
    jobs, rejected = [], 0
    for record in records:
        try:
            jobs.append(normalize_record(record))
        except RequestProblem:
            rejected += 1
    counts = {site: 0 for site in request["sites"]}
    for job in jobs:
        key = job["originPublisher"].lower()
        if key in counts:
            counts[key] += 1
    completed = datetime.now(timezone.utc)
    return {
        "requestId": request["requestId"], "status": "SUCCEEDED" if rejected == 0 else "PARTIAL_SUCCESS",
        "startedAt": started.isoformat(), "completedAt": completed.isoformat(),
        "query": request["query"], "jobs": jobs, "rejected": rejected,
        "boardResults": [{"site": site, "status": "SUCCEEDED", "discovered": count} for site, count in counts.items()],
    }


def scrape_in_bounded_process(request: dict[str, Any], timeout_seconds: int) -> dict[str, Any]:
    context = multiprocessing.get_context("spawn")
    output = context.Queue(maxsize=1)
    process = context.Process(target=_scrape_child, args=(request, output), daemon=True)
    process.start()
    try:
        status, value = output.get(timeout=timeout_seconds)
    except queue.Empty as exc:
        process.terminate()
        process.join(timeout=5)
        raise ScrapeTimeout("The approved job-board request exceeded its time limit") from exc
    finally:
        if process.is_alive():
            process.join(timeout=2)
            if process.is_alive():
                process.terminate()
                process.join(timeout=5)
    if status != "ok":
        raise RuntimeError("The approved job-board request failed")
    return value


def _scrape_child(request: dict[str, Any], output: Any) -> None:
    try:
        output.put(("ok", scrape_and_normalize(request)))
    except Exception:
        output.put(("error", None))


def normalize_record(record: dict[str, Any]) -> dict[str, Any]:
    site = _required_text(_value(record, "site"), "site", 40).lower()
    title = _required_text(_value(record, "title"), "title", 300)
    company = _required_text(_value(record, "company", "company_name"), "company", 200)
    source_url = canonical_url(_required_text(_value(record, "job_url"), "job_url", 2000))
    direct = _optional_text(_value(record, "job_url_direct"), 2000)
    apply_url = canonical_url(direct) if direct else source_url
    supplied_id = _optional_text(_value(record, "id"), 500)
    external_id = supplied_id or "url-" + hashlib.sha256(f"{site}|{source_url}".encode()).hexdigest()
    location = _optional_text(_value(record, "location"), 300)
    description = _optional_text(_value(record, "description"), 100000)
    remote = _value(record, "is_remote") is True
    job_type = (_optional_text(_value(record, "job_type"), 100) or "").replace("_", "").replace(" ", "").lower()
    interval = (_optional_text(_value(record, "interval"), 30) or "").lower()
    return {
        "externalId": external_id[:500], "originPublisher": site.upper(), "company": company,
        "title": title, "location": location, "countryCode": None,
        "workplaceType": "REMOTE" if remote else "UNSPECIFIED",
        "employmentType": EMPLOYMENT.get(job_type, "UNSPECIFIED"), "department": None, "team": None,
        "description": description, "applyUrl": apply_url, "sourceUrl": source_url,
        "salaryMinimum": _number(_value(record, "min_amount")), "salaryMaximum": _number(_value(record, "max_amount")),
        "salaryCurrency": _currency(_value(record, "currency")), "salaryInterval": INTERVAL.get(interval, "UNSPECIFIED"),
        "publishedAt": _instant(_value(record, "date_posted")), "sourceUpdatedAt": None, "expiresAt": None,
    }


def canonical_url(value: str) -> str:
    try:
        parsed = urlsplit(value)
    except ValueError as exc:
        raise RequestProblem("A job URL is invalid") from exc
    if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
        raise RequestProblem("A job URL must be a public HTTP(S) URL without credentials")
    query = urlencode([(key, val) for key, val in parse_qsl(parsed.query, keep_blank_values=True)
                       if not key.lower().startswith("utm_") and key.lower() not in TRACKING_KEYS])
    return urlunsplit((parsed.scheme.lower(), parsed.netloc.lower(), parsed.path or "/", query, ""))


def _value(record: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = record.get(key)
        if not _missing(value):
            return value
    return None


def _missing(value: Any) -> bool:
    return value is None or isinstance(value, float) and math.isnan(value)


def _required_text(value: Any, field: str, maximum: int, pattern: str | None = None) -> str:
    result = _optional_text(value, maximum)
    if result is None or pattern and re.fullmatch(pattern, result) is None:
        raise RequestProblem(f"{field} is invalid")
    return result


def _optional_text(value: Any, maximum: int) -> str | None:
    if _missing(value):
        return None
    result = str(value).strip()
    if not result or len(result) > maximum or any(ord(char) < 32 and char not in "\n\r\t" for char in result):
        return None
    return result


def _number(value: Any) -> float | None:
    if _missing(value):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) and result >= 0 else None


def _currency(value: Any) -> str | None:
    text = (_optional_text(value, 3) or "").upper()
    return text if re.fullmatch(r"[A-Z]{3}", text) else None


def _instant(value: Any) -> str | None:
    if _missing(value):
        return None
    if isinstance(value, datetime):
        moment = value if value.tzinfo else value.replace(tzinfo=timezone.utc)
        return moment.astimezone(timezone.utc).isoformat()
    if isinstance(value, date):
        return datetime(value.year, value.month, value.day, tzinfo=timezone.utc).isoformat()
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc).isoformat()
    except ValueError:
        return None

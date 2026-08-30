from __future__ import annotations

import hmac
import json
import logging
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .configuration import Settings
from .worker import RequestProblem, ScrapeTimeout, scrape_in_bounded_process, validate_request


SETTINGS = Settings.from_environment()
CAPACITY = threading.BoundedSemaphore(SETTINGS.maximum_concurrent_requests)
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOG = logging.getLogger("jobspy-worker")


class Handler(BaseHTTPRequestHandler):
    server_version = "JobSpyWorker/1"

    def do_GET(self) -> None:
        if self.path == "/healthz":
            self._json(200, {"status": "UP", "allowedSiteCount": len(SETTINGS.allowed_sites)})
        else:
            self._json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        if self.path != "/v1/scrape":
            self._json(404, {"error": "Not found"}); return
        supplied = self.headers.get("X-JobSpy-Worker-Token", "")
        if not hmac.compare_digest(supplied.encode(), SETTINGS.token.encode()):
            self._json(403, {"error": "Forbidden"}); return
        length = self.headers.get("Content-Length")
        if length is None or not length.isdigit() or int(length) > SETTINGS.maximum_body_bytes:
            self._json(413, {"error": "Request body is too large"}); return
        if not CAPACITY.acquire(blocking=False):
            self._json(429, {"error": "Worker capacity is currently full"}); return
        try:
            payload = json.loads(self.rfile.read(int(length)))
            request = validate_request(payload, SETTINGS)
            LOG.info("scrape_started requestId=%s sites=%s", request["requestId"], ",".join(request["sites"]))
            result = scrape_in_bounded_process(request, SETTINGS.request_timeout_seconds)
            LOG.info("scrape_completed requestId=%s jobs=%d rejected=%d", request["requestId"], len(result["jobs"]), result["rejected"])
            self._json(200, result)
        except (json.JSONDecodeError, RequestProblem) as problem:
            self._json(400, {"error": str(problem)})
        except ScrapeTimeout as problem:
            self._json(504, {"error": str(problem)})
        except Exception:
            LOG.exception("scrape_failed")
            self._json(502, {"error": "The approved job-board request failed"})
        finally:
            CAPACITY.release()

    def log_message(self, format: str, *args: object) -> None:
        LOG.info("http client=%s status=%s", self.client_address[0], args[1] if len(args) > 1 else "unknown")

    def _json(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(encoded)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()

from __future__ import annotations

import hmac
import json
import logging
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .scorer import METHOD, model, score_documents


TOKEN = os.getenv("ATS_SCORING_TOKEN", "")
MAX_BODY_BYTES = int(os.getenv("ATS_SCORING_MAX_BODY_BYTES", "131072"))
MAX_DOCUMENT_CHARACTERS = int(os.getenv("ATS_SCORING_MAX_DOCUMENT_CHARACTERS", "32000"))
CAPACITY = threading.BoundedSemaphore(int(os.getenv("ATS_SCORING_MAX_CONCURRENT_REQUESTS", "1")))
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOG = logging.getLogger("ats-scoring-worker")


def validate(payload: object) -> tuple[str, list[dict[str, str]]]:
    if not isinstance(payload, dict):
        raise ValueError("Request must be a JSON object")
    job = payload.get("jobDescription")
    documents = payload.get("documents")
    if not isinstance(job, str) or not job.strip():
        raise ValueError("jobDescription is required")
    if not isinstance(documents, list) or len(documents) != 2:
        raise ValueError("Exactly two documents are required")
    if len(job) > MAX_DOCUMENT_CHARACTERS:
        raise ValueError("jobDescription exceeds the character limit")
    validated: list[dict[str, str]] = []
    seen: set[str] = set()
    for value in documents:
        if not isinstance(value, dict):
            raise ValueError("Each document must be an object")
        identifier = value.get("id")
        text = value.get("text")
        if not isinstance(identifier, str) or identifier not in {"current", "generated"} or identifier in seen:
            raise ValueError("Document IDs must be unique current and generated values")
        if not isinstance(text, str) or not text.strip() or len(text) > MAX_DOCUMENT_CHARACTERS:
            raise ValueError("Each document requires bounded text")
        seen.add(identifier)
        validated.append({"id": identifier, "text": text.strip()})
    return job.strip(), validated


class Handler(BaseHTTPRequestHandler):
    server_version = "AtsSemanticScorer/1"

    def do_GET(self) -> None:
        if self.path == "/healthz":
            self._json(200, {"status": "UP", "model": os.getenv("ATS_MODEL_ID")})
        else:
            self._json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        if self.path != "/v1/score":
            self._json(404, {"error": "Not found"})
            return
        if TOKEN and not hmac.compare_digest(self.headers.get("X-ATS-Scoring-Token", ""), TOKEN):
            self._json(403, {"error": "Forbidden"})
            return
        length = self.headers.get("Content-Length")
        if length is None or not length.isdigit() or int(length) > MAX_BODY_BYTES:
            self._json(413, {"error": "Request body is too large"})
            return
        if not CAPACITY.acquire(blocking=False):
            self._json(429, {"error": "Scoring capacity is currently full"})
            return
        try:
            job, documents = validate(json.loads(self.rfile.read(int(length))))
            self._json(200, {"method": METHOD, "scores": score_documents(job, documents)})
        except (json.JSONDecodeError, ValueError) as problem:
            self._json(400, {"error": str(problem)})
        except Exception:
            LOG.exception("semantic_scoring_failed")
            self._json(502, {"error": "Semantic scoring failed"})
        finally:
            CAPACITY.release()

    def log_message(self, format: str, *args: object) -> None:
        LOG.info("http client=%s status=%s", self.client_address[0], args[1] if len(args) > 1 else "unknown")

    def _json(self, status: int, body: dict[str, object]) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(encoded)


if __name__ == "__main__":
    model()
    ThreadingHTTPServer(("0.0.0.0", 8091), Handler).serve_forever()

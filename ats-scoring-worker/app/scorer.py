from __future__ import annotations

import math
import os
import threading
from typing import Any


MODEL_ID = os.getenv("ATS_MODEL_ID", "0xnbk/nbk-ats-semantic-v1-en")
MODEL_PATH = os.getenv("ATS_MODEL_PATH", "/models/nbk-ats-semantic-v1-en")
MAX_TOKENS = int(os.getenv("ATS_SCORING_MAX_TOKENS", "2048"))
METHOD = "NBK_ATS_SEMANTIC_V1_EN_COSINE"
_runtime: tuple[Any, Any] | None = None
_lock = threading.Lock()


def model() -> tuple[Any, Any]:
    global _runtime
    if _runtime is None:
        with _lock:
            if _runtime is None:
                import onnxruntime as ort
                from transformers import AutoTokenizer

                tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH, local_files_only=True)
                session = ort.InferenceSession(
                    os.path.join(MODEL_PATH, "onnx", "model_quantized.onnx"),
                    providers=["CPUExecutionProvider"],
                )
                _runtime = (tokenizer, session)
    return _runtime


def score_documents(job_description: str, documents: list[dict[str, str]]) -> list[dict[str, float | str]]:
    import numpy as np

    texts = [job_description, *[document["text"] for document in documents]]
    tokenizer, session = model()
    encoded = tokenizer(
        texts,
        padding=True,
        truncation=True,
        max_length=MAX_TOKENS,
        return_tensors="np",
    )
    feed: dict[str, np.ndarray] = {}
    shape = encoded["input_ids"].shape
    for value in session.get_inputs():
        if value.name in encoded:
            feed[value.name] = encoded[value.name].astype(np.int64)
        elif value.name == "token_type_ids":
            feed[value.name] = np.zeros(shape, dtype=np.int64)
        elif value.name == "position_ids":
            feed[value.name] = np.tile(np.arange(shape[1], dtype=np.int64), (shape[0], 1))
        else:
            raise ValueError(f"Unsupported model input: {value.name}")
    token_embeddings = session.run(None, feed)[0]
    mask = encoded["attention_mask"].astype(np.float32)[..., None]
    embeddings = (token_embeddings * mask).sum(axis=1) / np.clip(mask.sum(axis=1), 1e-9, None)
    embeddings /= np.clip(np.linalg.norm(embeddings, axis=1, keepdims=True), 1e-9, None)
    job_embedding = embeddings[0]
    results: list[dict[str, float | str]] = []
    for document, embedding in zip(documents, embeddings[1:], strict=True):
        similarity = float(job_embedding @ embedding)
        if not math.isfinite(similarity):
            raise ValueError("Model produced a non-finite similarity")
        results.append({
            "id": document["id"],
            "score": round(max(0.0, min(100.0, similarity * 100.0)), 2),
            "cosineSimilarity": round(similarity, 6),
        })
    return results

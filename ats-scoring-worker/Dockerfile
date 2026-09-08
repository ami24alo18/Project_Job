FROM python:3.11.13-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models/huggingface \
    ATS_MODEL_ID=0xnbk/nbk-ats-semantic-v1-en \
    ATS_MODEL_REVISION=c9f566b05161f5913b8c20a7807017e407247be6 \
    ATS_MODEL_PATH=/models/nbk-ats-semantic-v1-en

RUN addgroup --system scorer && adduser --system --ingroup scorer scorer
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
RUN mkdir -p "${ATS_MODEL_PATH}" && \
    python -c "from huggingface_hub import snapshot_download; snapshot_download('${ATS_MODEL_ID}', revision='${ATS_MODEL_REVISION}', local_dir='${ATS_MODEL_PATH}', allow_patterns=['onnx/model_quantized.onnx','config.json','tokenizer.json','tokenizer_config.json','special_tokens_map.json'])" && \
    chown -R scorer:scorer /models

# Runtime scoring is deliberately offline and uses only the pinned artifact above.
ENV HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

USER scorer
EXPOSE 8091
CMD ["python", "-m", "app.server"]

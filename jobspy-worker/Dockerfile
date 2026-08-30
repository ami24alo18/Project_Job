FROM python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

RUN addgroup --system jobspy && adduser --system --ingroup jobspy jobspy
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
COPY THIRD_PARTY_NOTICES.md ./
USER jobspy
EXPOSE 8090
CMD ["python", "-m", "app.server"]

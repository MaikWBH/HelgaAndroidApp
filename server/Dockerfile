ARG PYTHON_IMAGE=python:3.12-slim-bookworm

# ── builder: Abhängigkeiten installieren (jstyleson liegt nur als Quellpaket vor) ──
FROM ${PYTHON_IMAGE} AS builder
ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /build
COPY requirements.txt .
RUN pip install --prefix=/install -r requirements.txt

# ── test: nur mit `docker build --target test .` ─────────────────────────────
FROM ${PYTHON_IMAGE} AS test
ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY --from=builder /install /usr/local
COPY requirements-dev.txt requirements.txt ./
RUN pip install -r requirements-dev.txt
COPY app/ ./app/
COPY scripts/ ./scripts/
COPY tests/ ./tests/
RUN python -c "from recipe_scrapers import scrape_html; from recipe_scrapers._exceptions import NoSchemaFoundInWildMode, WebsiteNotImplementedError" \
 && python -m pytest -q

# ── runtime ──────────────────────────────────────────────────────────────────
FROM ${PYTHON_IMAGE} AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 WEB_CONCURRENCY=2
RUN adduser --disabled-password --gecos "" --uid 1000 helga
WORKDIR /app
COPY --from=builder /install /usr/local
COPY --chown=helga:helga app/ ./app/
COPY --chown=helga:helga scripts/ ./scripts/
RUN mkdir -p data/images && chown helga:helga data data/images
USER helga
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health')"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

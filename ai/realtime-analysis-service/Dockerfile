FROM python:3.11-slim AS base

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src ./src
COPY config ./config

RUN pip install --no-cache-dir .

FROM base AS test

COPY tests ./tests

RUN pip install --no-cache-dir ".[dev]" && pytest

FROM base AS runtime

CMD ["python", "-m", "realtime_analysis"]

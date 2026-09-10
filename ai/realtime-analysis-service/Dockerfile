FROM python:3.11-slim

WORKDIR /app

COPY pyproject.toml README.md alembic.ini ./
COPY src ./src
COPY config ./config
COPY alembic ./alembic

RUN pip install --no-cache-dir .

CMD ["sh", "-c", "alembic upgrade head && exec python -m realtime_analysis"]

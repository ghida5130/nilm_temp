# syntax=docker/dockerfile:1
FROM python:3.11-slim AS base

WORKDIR /app

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_ROOT_USER_ACTION=ignore

# 의존성 레이어: pyproject.toml이 바뀔 때만 다시 실행된다.
# 캐시 마운트는 pyproject.toml이 바뀌어도 이미 받은 wheel을 재사용하게 한다.
COPY pyproject.toml ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install $(python -c "import tomllib; print(*tomllib.load(open('pyproject.toml', 'rb'))['project']['dependencies'])")

# 소스 레이어: 코드가 바뀌면 여기부터만 다시 실행된다. 의존성은 위에서 설치됐으므로 --no-deps.
COPY README.md ./
COPY src ./src
COPY config ./config
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-deps .

FROM base AS test

RUN --mount=type=cache,target=/root/.cache/pip \
    pip install $(python -c "import tomllib; print(*tomllib.load(open('pyproject.toml', 'rb'))['project']['optional-dependencies']['dev'])")
COPY tests ./tests
RUN pytest

FROM base AS runtime

CMD ["python", "-m", "realtime_analysis"]

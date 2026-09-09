#!/usr/bin/env bash
set -euo pipefail
: "${IMAGE_REPOSITORY:?IMAGE_REPOSITORY required}" "${IMAGE_TAG:?IMAGE_TAG required}" "${RELEASE_ID:?RELEASE_ID required}"
for service in api-gateway iot-device-service monitoring-service; do
  docker build -t "$IMAGE_REPOSITORY:$service-$RELEASE_ID" "backend/$service"
done
docker build -t "$IMAGE_REPOSITORY:mqtt-kafka-bridge-$RELEASE_ID" infrastructure/mqtt-kafka-bridge
frontend_args=()
if [[ -n "${FRONTEND_ENV:-}" ]]; then
  frontend_hash=$(sha256sum "$FRONTEND_ENV" | cut -d ' ' -f 1)
  frontend_args=(--secret "id=frontend_env,src=$FRONTEND_ENV" --build-arg "FRONTEND_ENV_HASH=$frontend_hash")
fi
docker build "${frontend_args[@]}" -t "$IMAGE_REPOSITORY:frontend-$RELEASE_ID" frontend

#!/usr/bin/env bash
set -euo pipefail
phase=$1
case "$phase" in
  a) target=a ;;
  b-base|b-analysis|b-bridge) target=b ;;
  *) echo "Unknown deployment phase" >&2; exit 2 ;;
esac
python3 infrastructure/scripts/release.py check --manifest /opt/nilm/release.json --target "$target"
cd /opt/nilm
case "$phase" in
  b-base)
    docker compose pull postgres kafka kafka-init
    docker compose up -d --wait --wait-timeout 300 postgres kafka
    docker compose run --rm --no-deps kafka-init
    ;;
  a)
    docker compose pull redis mosquitto keycloak
    docker compose up -d --wait --wait-timeout 360 redis mosquitto keycloak
    docker compose up -d --pull never --remove-orphans --wait --wait-timeout 360 api-gateway iot-device-service monitoring-service frontend
    ;;
  b-analysis)
    docker compose up -d --pull never --no-deps --wait --wait-timeout 180 realtime-analysis-service
    ;;
  b-bridge)
    docker compose up -d --pull never --no-deps --wait --wait-timeout 180 mqtt-kafka-bridge
    docker compose exec -T mqtt-kafka-bridge python smoke.py
    ;;
  *) echo "Unknown deployment phase" >&2; exit 2 ;;
esac

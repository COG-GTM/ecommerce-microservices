#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER_NAME="ws5-verify-consul"
CONSUL_URL="http://localhost:18500"
SERVICES=(product-service order-service inventory-service)

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d \
  --name "$CONTAINER_NAME" \
  -p 18500:8500 \
  -v "$REPO_ROOT/consul:/consul/config:ro" \
  -e CONSUL_DISABLE_PERM_MGMT=1 \
  hashicorp/consul:1.19 \
  agent -dev -server -ui -client=0.0.0.0

for _ in {1..60}; do
  if leader="$(curl -fsS "$CONSUL_URL/v1/status/leader" 2>/dev/null)" &&
    [[ "$leader" != '""' && -n "$leader" ]]; then
    break
  fi
  sleep 1
done

if [[ "${leader:-}" == '""' || -z "${leader:-}" ]]; then
  echo "Consul did not elect a leader within 60 seconds" >&2
  exit 1
fi

for service in "${SERVICES[@]}"; do
  registered=false
  for _ in {1..30}; do
    if curl -fsS "$CONSUL_URL/v1/catalog/service/$service" |
      jq -e --arg service "$service" \
        '[.[] | select(.ServiceName == $service and .ServiceAddress == $service and .ServicePort == 8080)] | length == 1' \
        >/dev/null; then
      registered=true
      break
    fi
    sleep 1
  done

  if [[ "$registered" != true ]]; then
    echo "Service registration assertion failed: $service" >&2
    exit 1
  fi
  echo "registered: $service (address=$service, port=8080)"
done

echo "Consul declarative registration verification passed"

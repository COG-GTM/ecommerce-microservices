#!/usr/bin/env bash
# Build the Morrisons till stack from source and start it; returns once the till is usable end to end.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/deploy/till/docker-compose.till.yml")
GATEWAY="http://localhost:${TILL_GATEWAY_PORT:-8181}"
WEBAPP="http://localhost:${TILL_WEBAPP_PORT:-5174}"

if [[ "${TILL_SKIP_BUILD:-0}" != "1" ]]; then
  echo "==> Building services from source (JDK 17)"
  "$ROOT/scripts/till/mvn.sh" -q -B -DskipTests package
fi

echo "==> Starting containers"
"${COMPOSE[@]}" up -d --build --wait --wait-timeout "${TILL_WAIT_TIMEOUT:-420}"

if [[ "${TILL_GATEWAY_PROFILE:-dev}" != "dev" ]]; then
  echo "==> Secured gateway profile: containers healthy; /api/till/** needs a JWT, skipping anonymous readiness probe"
else
echo "==> Waiting for till API via gateway (Eureka registration + catalogue seed)"
deadline=$((SECONDS + ${TILL_WAIT_TIMEOUT:-420}))
until curl -fs "$GATEWAY/api/till/session?tillId=healthcheck" | grep -q '"backendConnected":true' \
   && [[ "$(curl -fs "$GATEWAY/api/till/products?quickkey=true" | grep -o '"barcode"' | wc -l)" -ge 8 ]] \
   && curl -fs "$WEBAPP/api/till/products/1001" >/dev/null \
   && curl -fs "$WEBAPP/api/till/products/4002" >/dev/null \
   && curl -fs "$GATEWAY/api/till/products/2202" >/dev/null; do
  if (( SECONDS > deadline )); then
    echo "Till stack did not become ready; recent logs:" >&2
    "${COMPOSE[@]}" logs --tail 40 api-gateway till-service product-service >&2
    exit 1
  fi
  sleep 3
done
fi

cat <<MSG
==> Morrisons till stack is up
    Till webapp   $WEBAPP          (presenter API chips: $WEBAPP/?api=1)
    API gateway   $GATEWAY/api/till/session
    Eureka        http://localhost:${TILL_EUREKA_PORT:-8761}
    Keycloak      http://localhost:${TILL_KEYCLOAK_PORT:-8080}
    Run tests:    make till-e2e     Stop: make till-down
MSG

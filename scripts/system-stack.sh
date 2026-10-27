#!/usr/bin/env bash
# Builds and starts the docker-compose.test.yml topology and blocks until the full
# order path (Keycloak -> gateway -> product/order -> inventory) is serving.
#   scripts/system-stack.sh up     # package jars, build images, start, wait
#   scripts/system-stack.sh wait   # wait only
#   scripts/system-stack.sh down   # stop and remove containers/volumes
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f docker-compose.test.yml)
REALM=realms/spring-boot-microservices-realm.json
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
GATEWAY_URL=${GATEWAY_URL:-http://localhost:8181}
STOREFRONT_URL=${STOREFRONT_URL:-http://localhost:3001}
TIMEOUT_SECONDS=${STACK_TIMEOUT_SECONDS:-420}

# The service-account client secret already lives in the committed realm export; read it at
# runtime instead of duplicating it into env files.
if [[ -z "${OAUTH_CLIENT_SECRET:-}" ]]; then
  OAUTH_CLIENT_SECRET=$(python3 -c "import json,sys; print(next(c['secret'] for c in json.load(open(sys.argv[1]))['clients'] if c['clientId']=='spring-cloud-client'))" "$REALM")
  export OAUTH_CLIENT_SECRET
fi

wait_for() {
  local description=$1; shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until "$@" >/dev/null 2>&1; do
    if (( SECONDS > deadline )); then
      echo "Timed out waiting for: $description" >&2
      "${COMPOSE[@]}" ps >&2 || true
      return 1
    fi
    sleep 3
  done
  echo "ready: $description"
}

token() {
  curl -sf -X POST "$KEYCLOAK_URL/realms/spring-boot-microservices-realm/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d client_id=spring-cloud-client --data-urlencode "client_secret=$OAUTH_CLIENT_SECRET" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])"
}

registered() {
  curl -sf -H 'Accept: application/json' http://localhost:8761/eureka/apps | python3 -c "
import json, sys
apps = {a['name'] for a in json.load(sys.stdin)['applications']['application']}
sys.exit(0 if {'API-GATEWAY','PRODUCT-SERVICE','ORDER-SERVICE','INVENTORY-SERVICE','NOTIFICATION-SERVICE'} <= apps else 1)"
}

gateway_routes_products() {
  curl -sf -H "Authorization: Bearer $(token)" "$GATEWAY_URL/api/product"
}

wait_stack() {
  wait_for "keycloak token endpoint" token
  wait_for "eureka registrations" registered
  wait_for "gateway -> product-service" gateway_routes_products
  wait_for "storefront" curl -sf "$STOREFRONT_URL/healthz"
}

case "${1:-up}" in
  up)
    if [[ "${SKIP_PACKAGE:-false}" != "true" ]]; then
      mvn -B -q package -DskipTests
    fi
    "${COMPOSE[@]}" up -d --build
    wait_stack
    ;;
  wait) wait_stack ;;
  down) "${COMPOSE[@]}" down -v --remove-orphans ;;
  logs) "${COMPOSE[@]}" logs --no-color ;;
  *) echo "usage: $0 up|wait|down|logs" >&2; exit 2 ;;
esac

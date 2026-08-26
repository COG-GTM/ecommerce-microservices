#!/usr/bin/env bash
# E2E golden-path test for Micro Marketplace (see TEST_PLAN.md).
# Requires the full stack running via `docker compose up -d`, plus curl and jq.
set -u

GATEWAY="${GATEWAY:-http://localhost:8181}"
KEYCLOAK="${KEYCLOAK:-http://keycloak:8080}"
REALM="spring-boot-microservices-realm"
CLIENT_ID="spring-cloud-client"
CLIENT_SECRET="${CLIENT_SECRET:-$(python3 -c "
import json,sys
d=json.load(open('$(dirname "$0")/../realms/spring-boot-microservices-realm.json'))
print(next(c['secret'] for c in d['clients'] if c['clientId']=='$CLIENT_ID'))
" 2>/dev/null)}"
if [ -z "$CLIENT_SECRET" ]; then
  echo "ERROR: could not resolve CLIENT_SECRET (set the CLIENT_SECRET env var or ensure python3 and realms/spring-boot-microservices-realm.json are available)" >&2
  exit 1
fi
SKU="e2e_sku_$$"
OOS_SKU="e2e_oos_sku_$$"

PASS=0; FAIL=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS: $1"; PASS=$((PASS+1));
  else echo "FAIL: $1 (expected: $2, got: $3)"; FAIL=$((FAIL+1)); fi
}
contains() { # name needle haystack
  case "$3" in *"$2"*) echo "PASS: $1"; PASS=$((PASS+1));;
    *) echo "FAIL: $1 (missing: $2, got: $3)"; FAIL=$((FAIL+1));; esac
}

echo "== Token acquisition =="
TOKEN=$(curl -s -X POST "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" | jq -r .access_token)
check "Keycloak issues access token" "true" "$([ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] && echo true || echo false)"
AUTH="Authorization: Bearer $TOKEN"

echo "== Security =="
code=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY/api/product")
check "Gateway rejects unauthenticated request (401)" "401" "$code"

echo "== Golden path =="
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/product" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Iphone 15","description":"Apple Iphone 15","price":1500}')
check "Create product returns 201" "201" "$code"

body=$(curl -s "$GATEWAY/api/product" -H "$AUTH")
contains "Product list contains created product" "Iphone 15" "$body"

docker exec mysql-inventory mysql -uibatulanand -ppassword inventory_service -e \
  "CREATE TABLE IF NOT EXISTS t_inventory (id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_code VARCHAR(255), quantity INT); \
   INSERT INTO t_inventory (sku_code, quantity) VALUES ('$SKU', 10), ('$OOS_SKU', 0);" 2>/dev/null
echo "Seeded inventory for skus $SKU (qty 10) and $OOS_SKU (qty 0)"

ORDER_TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)

body=$(curl -s -X POST "$GATEWAY/api/order" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"orderLineItemsDtoList\":[{\"skuCode\":\"$SKU\",\"price\":1500,\"quantity\":1}]}")
contains "In-stock order is placed" "Order Placed Successfully" "$body"

logs=""
for _ in $(seq 1 12); do
  logs=$(docker logs notification-service --since "$ORDER_TS" 2>&1)
  case "$logs" in *"Received Notification for Order"*) break;; esac
  sleep 5
done
contains "Notification consumed from notificationTopic" "Received Notification for Order" "$logs"

echo "== Negative scenarios =="
body=$(curl -s -X POST "$GATEWAY/api/order" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"orderLineItemsDtoList\":[{\"skuCode\":\"$OOS_SKU\",\"price\":10,\"quantity\":1}]}")
contains "Out-of-stock order returns fallback message" "Oops! Something went wrong" "$body"

docker stop inventory-service >/dev/null
body=$(curl -s -X POST "$GATEWAY/api/order" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"orderLineItemsDtoList\":[{\"skuCode\":\"$SKU\",\"price\":1500,\"quantity\":1}]}")
docker start inventory-service >/dev/null
contains "Resilience4j fallback when inventory is down" "Oops! Something went wrong" "$body"

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]

#!/usr/bin/env bash
# Fails if docker-compose.yml publishes the MySQL/MongoDB ports on all interfaces,
# ships built-in database passwords, or runs MongoDB without authentication.
set -euo pipefail
cd "$(dirname "$0")/.."

if env -i PATH="$PATH" HOME="$HOME" docker compose --env-file /dev/null config >/dev/null 2>&1; then
  echo "FAIL: compose config renders without database credentials (built-in defaults present)"
  exit 1
fi

env_file=$(mktemp)
trap 'rm -f "$env_file"' EXIT
sed 's/CHANGE_ME/check-placeholder-secret/' .env.example > "$env_file"

env -i PATH="$PATH" HOME="$HOME" docker compose --env-file "$env_file" config --format json | python3 -c '
import json, sys
cfg = json.load(sys.stdin)["services"]
errors = []
for name in ("mysql-order", "mysql-inventory", "mongo"):
    for port in cfg[name].get("ports", []):
        if port.get("host_ip") not in ("127.0.0.1", "::1"):
            where = port.get("host_ip") or "all interfaces"
            errors.append(name + ": port " + str(port.get("published")) + " published on " + where)
weak = {"root", "password", "mysql", "admin", ""}
for name in ("mysql-order", "mysql-inventory"):
    env = cfg[name].get("environment", {})
    for key in ("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD"):
        if (env.get(key) or "") in weak:
            errors.append(f"{name}: {key} is empty or a trivial default")
mongo_env = cfg["mongo"].get("environment", {})
for key in ("MONGO_INITDB_ROOT_USERNAME", "MONGO_INITDB_ROOT_PASSWORD"):
    if not mongo_env.get(key):
        errors.append(f"mongo: {key} not set (MongoDB would start without access control)")
uri = cfg["product-service"]["environment"].get("SPRING_DATA_MONGODB_URI", "")
if "@" not in uri:
    errors.append("product-service: SPRING_DATA_MONGODB_URI has no credentials")
for e in errors:
    print("FAIL:", e)
if errors:
    sys.exit(1)
print("OK: database ports bound to loopback, credentials required from env, MongoDB auth enabled")
'

#!/usr/bin/env bash
# Stop the till stack and remove its containers and volumes (fresh data on next till-up).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
docker compose -f "$ROOT/deploy/till/docker-compose.till.yml" down -v --remove-orphans

#!/bin/sh
# Refuses to start Grafana without a strong admin password. When
# GRAFANA_ADMIN_PASSWORD changes, applies it once to an existing grafana.db
# (GF_SECURITY_ADMIN_PASSWORD alone only takes effect when the database is
# first created). Passwords later changed in the Grafana UI are kept until
# GRAFANA_ADMIN_PASSWORD changes again.
set -eu

fail() {
  echo "grafana: $1. Set GRAFANA_ADMIN_PASSWORD in .env (see .env.example)." >&2
  exit 1
}

password="${GF_SECURITY_ADMIN_PASSWORD:-}"
[ -n "$password" ] || fail "GRAFANA_ADMIN_PASSWORD is not set"
[ "${#password}" -ge 16 ] || fail "GRAFANA_ADMIN_PASSWORD must be at least 16 characters"
case "$password" in
  *CHANGE_ME*) fail "GRAFANA_ADMIN_PASSWORD still contains the CHANGE_ME placeholder" ;;
  *"
"*) fail "GRAFANA_ADMIN_PASSWORD must be a single line" ;;
esac

data="${GF_PATHS_DATA:-/var/lib/grafana}"
marker="$data/.admin-password-applied"
fingerprint="$(printf '%s' "$password" | sha256sum | cut -d' ' -f1)"

if [ -f "$data/grafana.db" ] && [ "$(cat "$marker" 2>/dev/null || true)" != "$fingerprint" ]; then
  if command -v grafana >/dev/null 2>&1; then
    set -- grafana cli
  else
    set -- grafana-cli
  fi
  printf '%s\n' "$password" | "$@" \
    --homepath "${GF_PATHS_HOME:-/usr/share/grafana}" \
    --config "${GF_PATHS_CONFIG:-/etc/grafana/grafana.ini}" \
    admin reset-admin-password --password-from-stdin >/dev/null \
    || fail "could not apply GRAFANA_ADMIN_PASSWORD to the existing grafana.db"
fi
(umask 077 && printf '%s\n' "$fingerprint" > "$marker")

exec /run.sh

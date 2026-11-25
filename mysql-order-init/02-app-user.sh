#!/bin/bash
# Creates (or re-keys) the order-service application account with data-only
# access to order_service. Run by the MySQL image entrypoint on first
# initialisation; safe to re-run, see README for existing data directories.
set -eo pipefail

app_user="${ORDER_DB_APP_USER:-order_app}"
app_password="${ORDER_DB_APP_PASSWORD:-}"

if [[ ! "$app_user" =~ ^[A-Za-z0-9_]{1,32}$ ]] || [[ "${app_user,,}" == "root" ]]; then
    echo "02-app-user.sh: ORDER_DB_APP_USER must be 1-32 letters, digits or '_' and not root" >&2
    exit 1
fi
if [[ -z "$app_password" ]]; then
    echo "02-app-user.sh: ORDER_DB_APP_PASSWORD must be set" >&2
    exit 1
fi

sql_password=${app_password//\\/\\\\}
sql_password=${sql_password//\'/\'\'}
cnf_password=${MYSQL_ROOT_PASSWORD//\\/\\\\}
cnf_password=${cnf_password//\"/\\\"}

client_cnf=$(mktemp)
trap 'rm -f "$client_cnf"' EXIT
printf '[client]\nuser=root\npassword="%s"\n' "$cnf_password" > "$client_cnf"

# Escape '_' so the grant is not a wildcard, unless partial_revokes (MySQL 8+) makes names literal.
db_grant='order\_service'
if [[ "$(mysql --defaults-extra-file="$client_cnf" --protocol=socket -N -e "SHOW GLOBAL VARIABLES LIKE 'partial_revokes'" | cut -f2)" == "ON" ]]; then
    db_grant='order_service'
fi

mysql --defaults-extra-file="$client_cnf" --protocol=socket <<SQL
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${sql_password}';
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${sql_password}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${app_user}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${db_grant}\`.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL

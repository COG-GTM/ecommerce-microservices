#!/bin/bash
# Creates the order-service application account with data-only access to
# order_service. Sourced by the MySQL image entrypoint on first initialisation.
set -euo pipefail

app_user="${ORDER_DB_APP_USER:-order_app}"
app_password="${ORDER_DB_APP_PASSWORD:-}"

if [[ ! "$app_user" =~ ^[A-Za-z0-9_]{1,32}$ ]] || [[ "${app_user,,}" == "root" ]]; then
    echo "02-app-user.sh: ORDER_DB_APP_USER must be 1-32 letters, digits or '_' and not root" >&2
    exit 1
fi
if [[ -z "$app_password" || "$app_password" == *"'"* || "$app_password" == *"\\"* ]]; then
    echo "02-app-user.sh: ORDER_DB_APP_PASSWORD must be set and must not contain quotes or backslashes" >&2
    exit 1
fi

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`order_service\`.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL

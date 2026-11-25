#!/usr/bin/env bash
# Container healthcheck without curl: GET /actuator/health on the given port and require status UP.
exec 3<>"/dev/tcp/127.0.0.1/${1:-8080}" || exit 1
printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
grep -q '"status":"UP"' <&3

#!/usr/bin/env bash
# Run Maven with a JDK 17 (Spring Boot 3.1 + Lombok in this repo do not compile on newer JDKs).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ -z "${TILL_JAVA_HOME:-}" ]]; then
  for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/temurin-17-jdk-amd64 /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]] && "$candidate/bin/java" -version 2>&1 | grep -q 'version "17'; then
      TILL_JAVA_HOME="$candidate"; break
    fi
  done
fi
[[ -n "${TILL_JAVA_HOME:-}" ]] || { echo "JDK 17 not found; set TILL_JAVA_HOME" >&2; exit 1; }
cd "$ROOT" && JAVA_HOME="$TILL_JAVA_HOME" exec mvn "$@"

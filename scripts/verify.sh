#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -z "${JAVA_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
  detected_java_home="$(java -XshowSettings:properties -version 2>&1 \
    | awk -F ' = ' '/^[[:space:]]*java.home = / { print $2; exit }')"
  if [[ -n "$detected_java_home" ]]; then
    if command -v cygpath >/dev/null 2>&1; then
      export JAVA_HOME="$(cygpath -u "$detected_java_home")"
    else
      export JAVA_HOME="$detected_java_home"
    fi
  fi
fi

run_maven_tests() {
  local service="$1"
  (
    cd product-service
    if [[ "$service" == "product-service" ]]; then
      bash ./mvnw -q test
    else
      bash ./mvnw -q -f "../$service/pom.xml" test
    fi
  )
}

run_maven_tests api-gateway
run_maven_tests discovery-service
run_maven_tests user-service
run_maven_tests product-service
run_maven_tests media-service

(
  cd frontend
  npm ci
  npm test -- --watch=false
  npm run build
)

JWT_SECRET="${JWT_SECRET:-local-audit-secret-at-least-32-characters}" \
  docker compose config --quiet

(
  cd frontend
  npm audit --omit=dev
)

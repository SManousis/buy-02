#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SONAR_HOST_URL:-}" || -z "${SONAR_TOKEN:-}" ]]; then
  echo "Skipping backend Sonar upload because SONAR_HOST_URL and SONAR_TOKEN are not configured."
  exit 0
fi

if [[ "${SONAR_HOST_URL}" == *localhost* ]]; then
  echo "Skipping backend Sonar upload because localhost is not reachable from GitHub-hosted runners. Use a real Sonar host URL for CI."
  exit 0
fi

services=(
  api-gateway
  discovery-service
  media-service
  product-service
  user-service
)

for service in "${services[@]}"; do
  echo "============================================================"
  echo "Running backend build/test for ${service}"
  echo "============================================================"

  if [[ -d "${service}" ]]; then
    if [[ -x "${service}/mvnw" ]]; then
      (cd "${service}" && ./mvnw -q test jacoco:report)
    else
      (cd "${service}" && mvn -q test jacoco:report)
    fi

    if [[ -x "${service}/mvnw" ]]; then
      (cd "${service}" && ./mvnw -q sonar:sonar \
        -Dsonar.projectKey="buy-01-${service}" \
        -Dsonar.projectName="${service}" \
        -Dsonar.host.url="${SONAR_HOST_URL}" \
        -Dsonar.token="${SONAR_TOKEN}" \
        -Dsonar.coverage.jacoco.xmlReportPaths="target/site/jacoco/jacoco.xml")
    else
      (cd "${service}" && mvn -q sonar:sonar \
        -Dsonar.projectKey="buy-01-${service}" \
        -Dsonar.projectName="${service}" \
        -Dsonar.host.url="${SONAR_HOST_URL}" \
        -Dsonar.token="${SONAR_TOKEN}" \
        -Dsonar.coverage.jacoco.xmlReportPaths="target/site/jacoco/jacoco.xml")
    fi
  else
    echo "Service directory not found: ${service}"
    exit 1
  fi
done

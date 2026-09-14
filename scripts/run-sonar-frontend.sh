#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SONAR_HOST_URL:-}" || -z "${SONAR_TOKEN:-}" ]]; then
  echo "Skipping frontend Sonar upload because SONAR_HOST_URL and SONAR_TOKEN are not configured."
  exit 0
fi

if [[ "${SONAR_HOST_URL}" == *localhost* ]]; then
  echo "Skipping frontend Sonar upload because localhost is not reachable from GitHub-hosted runners. Use a real Sonar host URL for CI."
  exit 0
fi

cd "$(dirname "$0")/../frontend"

npm ci
npm run build
npx sonar-scanner \
  -Dproject.settings=sonar-project.properties \
  -Dsonar.host.url="${SONAR_HOST_URL}" \
  -Dsonar.token="${SONAR_TOKEN}" \
  -Dsonar.qualitygate.wait=true \
  -Dsonar.qualitygate.timeout=600

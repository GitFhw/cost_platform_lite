#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${1:-${SCRIPT_DIR}/../runtime/cost-lite-server-1.0.0.jar}"

: "${COST_LITE_DB_USERNAME:?COST_LITE_DB_USERNAME is required}"
: "${COST_LITE_DB_PASSWORD:?COST_LITE_DB_PASSWORD is required}"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Cost Lite Oracle Jar not found: ${JAR_PATH}" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=oracle
export COST_LITE_SERVER_PORT="${COST_LITE_SERVER_PORT:-18082}"
export COST_LITE_DB_DRIVER="${COST_LITE_DB_DRIVER:-oracle.jdbc.OracleDriver}"

if [[ -z "${COST_LITE_DB_URL:-}" ]]; then
  DB_HOST="${COST_LITE_DB_HOST:-127.0.0.1}"
  DB_PORT="${COST_LITE_DB_PORT:-1521}"
  DB_SERVICE="${COST_LITE_DB_SERVICE:-${COST_LITE_DB_NAME:-FREEPDB1}}"
  export COST_LITE_DB_URL="jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${DB_SERVICE}"
fi

echo "Starting Cost Lite on port ${COST_LITE_SERVER_PORT} with Oracle database URL ${COST_LITE_DB_URL}..."
exec java -jar "${JAR_PATH}"

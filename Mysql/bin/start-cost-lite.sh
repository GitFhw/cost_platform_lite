#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${1:-${SCRIPT_DIR}/../runtime/cost-lite-server-1.0.0.jar}"

: "${COST_LITE_DB_HOST:?COST_LITE_DB_HOST is required}"
: "${COST_LITE_DB_PORT:?COST_LITE_DB_PORT is required}"
: "${COST_LITE_DB_NAME:?COST_LITE_DB_NAME is required}"
: "${COST_LITE_DB_USERNAME:?COST_LITE_DB_USERNAME is required}"
: "${COST_LITE_DB_PASSWORD:?COST_LITE_DB_PASSWORD is required}"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Cost Lite Jar not found: ${JAR_PATH}" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=mysql
export COST_LITE_SERVER_PORT="${COST_LITE_SERVER_PORT:-18080}"
export COST_LITE_DB_DRIVER=com.mysql.cj.jdbc.Driver
export COST_LITE_DB_URL="jdbc:mysql://${COST_LITE_DB_HOST}:${COST_LITE_DB_PORT}/${COST_LITE_DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"

echo "Starting Cost Lite on port ${COST_LITE_SERVER_PORT} with MySQL database ${COST_LITE_DB_NAME}..."
exec java -jar "${JAR_PATH}"

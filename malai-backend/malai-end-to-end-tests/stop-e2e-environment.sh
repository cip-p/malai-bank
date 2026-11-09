#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "===== Stopping E2E Docker environment ====="
docker compose -f "$SCRIPT_DIR/docker-compose.e2e.yml" down -v

echo "===== E2E environment stopped ====="

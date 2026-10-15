#!/usr/bin/env sh
# Starts the orchestrator REST service on http://localhost:8080 (human approvals through the API).
set -eu
cd "$(dirname "$0")/.."
exec mvn -B -pl orchestrator spring-boot:run

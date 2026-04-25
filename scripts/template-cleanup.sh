#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

mvn -q -DskipTests -f "${REPO_ROOT}/pom.xml" exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool "$@"

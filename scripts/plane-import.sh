#!/usr/bin/env bash
#
# plane-import.sh — Create EMF project, labels, and import tasks into Plane
#
# Prerequisites:
#   - Plane instance running at PLANE_URL
#   - API key generated from Profile > API Tokens
#   - Workspace already created via Plane UI
#   - jq installed
#
# Usage:
#   export PLANE_API_KEY="plane_api_..."
#   export PLANE_URL="https://plane.rzware.com"
#   export PLANE_WORKSPACE="<workspace-slug>"
#   ./scripts/plane-import.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TASKS_FILE="${SCRIPT_DIR}/plane-tasks.json"

# --- Configuration ---
: "${PLANE_API_KEY:?Set PLANE_API_KEY to your Plane API token}"
: "${PLANE_URL:?Set PLANE_URL to your Plane instance URL (e.g. https://plane.rzware.com)}"
: "${PLANE_WORKSPACE:?Set PLANE_WORKSPACE to your workspace slug}"

API_BASE="${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}"

# Rate limit: Plane allows 60 requests/minute. We add a small delay between calls.
RATE_DELAY=1

# --- Helpers ---

api() {
    local method="$1"
    local path="$2"
    shift 2
    local url="${API_BASE}${path}"

    local response
    response=$(curl -sf -X "${method}" "${url}" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${PLANE_API_KEY}" \
        "$@" 2>&1) || {
        echo "ERROR: ${method} ${url} failed" >&2
        echo "${response}" >&2
        return 1
    }
    echo "${response}"
    sleep "${RATE_DELAY}"
}

log() {
    echo "[$(date '+%H:%M:%S')] $*"
}

# --- Step 1: Create Project ---

create_project() {
    local name identifier description
    name=$(jq -r '.project.name' "${TASKS_FILE}")
    identifier=$(jq -r '.project.identifier' "${TASKS_FILE}")
    description=$(jq -r '.project.description' "${TASKS_FILE}")

    log "Creating project: ${name} (${identifier})"

    local response
    response=$(api POST "/projects/" \
        -d "$(jq -n \
            --arg name "${name}" \
            --arg identifier "${identifier}" \
            --arg description "${description}" \
            '{name: $name, identifier: $identifier, description: $description}')")

    PROJECT_ID=$(echo "${response}" | jq -r '.id')
    log "Project created: ${PROJECT_ID}"
}

# --- Step 2: Create Labels ---

declare -A LABEL_IDS

create_labels() {
    log "Creating labels..."

    # Work stream labels (blue-ish colors)
    local -A stream_colors=(
        ["Tenant Core"]="#1e40af"
        ["Users & Auth"]="#7c3aed"
        ["Permissions"]="#be185d"
        ["Sharing"]="#b45309"
        ["OIDC"]="#047857"
        ["Cross-cutting"]="#64748b"
    )

    while IFS= read -r label; do
        local color="${stream_colors[${label}]:-#6b7280}"
        local response
        response=$(api POST "/projects/${PROJECT_ID}/labels/" \
            -d "$(jq -n --arg name "${label}" --arg color "${color}" \
                '{name: $name, color: $color}')")
        LABEL_IDS["${label}"]=$(echo "${response}" | jq -r '.id')
        log "  Label: ${label} -> ${LABEL_IDS[${label}]}"
    done < <(jq -r '.labels.work_streams[]' "${TASKS_FILE}")

    # Phase labels (green-ish colors)
    local -A phase_colors=(
        ["Phase 1"]="#16a34a"
        ["Phase 2"]="#2563eb"
        ["Phase 3"]="#9333ea"
        ["Phase 4"]="#dc2626"
        ["Phase 5"]="#ea580c"
        ["Phase 6"]="#0891b2"
    )

    while IFS= read -r label; do
        local color="${phase_colors[${label}]:-#6b7280}"
        local response
        response=$(api POST "/projects/${PROJECT_ID}/labels/" \
            -d "$(jq -n --arg name "${label}" --arg color "${color}" \
                '{name: $name, color: $color}')")
        LABEL_IDS["${label}"]=$(echo "${response}" | jq -r '.id')
        log "  Label: ${label} -> ${LABEL_IDS[${label}]}"
    done < <(jq -r '.labels.phases[]' "${TASKS_FILE}")
}

# --- Step 3: Get Default State ---

get_default_state() {
    log "Fetching project states..."
    local response
    response=$(api GET "/projects/${PROJECT_ID}/states/")

    # Use the first "Backlog" state, or fall back to first state
    DEFAULT_STATE_ID=$(echo "${response}" | jq -r '
        .results |
        (map(select(.group == "backlog"))[0] //
         map(select(.group == "unstarted"))[0] //
         .[0]) | .id')

    log "Default state: ${DEFAULT_STATE_ID}"
}

# --- Step 4: Import Tasks ---

import_tasks() {
    local total
    total=$(jq '.tasks | length' "${TASKS_FILE}")
    log "Importing ${total} tasks..."

    local i=0
    while IFS= read -r task; do
        i=$((i + 1))
        local task_id task_name priority description
        task_id=$(echo "${task}" | jq -r '.id')
        task_name=$(echo "${task}" | jq -r '.name')
        priority=$(echo "${task}" | jq -r '.priority')
        description=$(echo "${task}" | jq -r '.description')

        # Build label UUID array
        local label_uuids="[]"
        while IFS= read -r label; do
            local uuid="${LABEL_IDS[${label}]:-}"
            if [[ -n "${uuid}" ]]; then
                label_uuids=$(echo "${label_uuids}" | jq --arg id "${uuid}" '. + [$id]')
            fi
        done < <(echo "${task}" | jq -r '.labels[]')

        # Format description as HTML for Plane
        local desc_html
        desc_html=$(echo "${description}" | sed 's/$/<br>/g' | sed 's/^/<p>/;s/$/<\/p>/' | head -1)
        desc_html="<p><strong>[${task_id}]</strong></p><p>${description}</p>"

        log "  [${i}/${total}] ${task_id}: ${task_name}"

        api POST "/projects/${PROJECT_ID}/issues/" \
            -d "$(jq -n \
                --arg name "[${task_id}] ${task_name}" \
                --arg description_html "${desc_html}" \
                --arg priority "${priority}" \
                --arg state "${DEFAULT_STATE_ID}" \
                --argjson labels "${label_uuids}" \
                '{
                    name: $name,
                    description_html: $description_html,
                    priority: $priority,
                    state: $state,
                    labels: $labels
                }')" > /dev/null

    done < <(jq -c '.tasks[]' "${TASKS_FILE}")
}

# --- Main ---

main() {
    log "Starting Plane import for EMF project"
    log "Plane URL: ${PLANE_URL}"
    log "Workspace: ${PLANE_WORKSPACE}"
    log ""

    if [[ ! -f "${TASKS_FILE}" ]]; then
        echo "ERROR: Tasks file not found: ${TASKS_FILE}" >&2
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        echo "ERROR: jq is required but not installed" >&2
        exit 1
    fi

    create_project
    create_labels
    get_default_state
    import_tasks

    log ""
    log "Import complete!"
    log "Project: ${PLANE_URL}/${PLANE_WORKSPACE}/projects/${PROJECT_ID}/issues"
}

main "$@"

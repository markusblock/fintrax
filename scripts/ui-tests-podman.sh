#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-focused}"
vnc_mode="${2:-}"

if [[ $# -gt 2 ]] || [[ -n "$vnc_mode" && "$vnc_mode" != "--vnc" ]]; then
    echo "Usage: $0 [focused|all] [--vnc]" >&2
    exit 2
fi

case "$mode" in
    focused|all) ;;
    *)
        echo "Unknown test mode '$mode'. Usage: $0 [focused|all]" >&2
        exit 2
        ;;
esac

if ! command -v podman >/dev/null 2>&1; then
    echo "Podman is required. Install Podman and retry." >&2
    exit 1
fi

if ! podman info >/dev/null 2>&1; then
    echo "Podman is not running. On macOS, start its VM with 'podman machine start'." >&2
    exit 1
fi

podman_args=(
    --platform linux/amd64
    --rm
    --userns=keep-id
    --volume "$repo_root:/workspace:rw"
    --workdir /workspace
    --env GRADLE_USER_HOME=/workspace/.gradle-podman
    --env "JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=14 -XX:ActiveProcessorCount=2"
)
runner_args=("$mode")

if [[ "$vnc_mode" == "--vnc" ]]; then
    vnc_port="${FINTRAX_UI_VNC_PORT:-5900}"
    if [[ ! "$vnc_port" =~ ^[0-9]+$ ]]; then
        echo "FINTRAX_UI_VNC_PORT must be a port number between 1 and 65535." >&2
        exit 2
    fi
    vnc_port_number=$((10#$vnc_port))
    if ((vnc_port_number < 1 || vnc_port_number > 65535)); then
        echo "FINTRAX_UI_VNC_PORT must be a port number between 1 and 65535." >&2
        exit 2
    fi
    vnc_port="$vnc_port_number"
    podman_args+=(--publish "127.0.0.1:${vnc_port}:5900")
    runner_args+=(--vnc)
fi

image="fintrax-ui-tests:local"
podman build \
    --platform linux/amd64 \
    --file "$repo_root/tools/ui-tests/Containerfile" \
    --tag "$image" \
    "$repo_root/tools/ui-tests"

podman run "${podman_args[@]}" "$image" \
    /usr/local/bin/run-ui-tests "${runner_args[@]}"

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-focused}"
screen_sharing_pid=""

close_screen_sharing() {
    if [[ -n "$screen_sharing_pid" ]] && kill -0 "$screen_sharing_pid" 2>/dev/null; then
        kill -TERM "$screen_sharing_pid" 2>/dev/null || true
    fi
}
trap close_screen_sharing EXIT

open_screen_sharing() {
    local previous_pid current_pid
    previous_pid="$(pgrep -nx "Screen Sharing" 2>/dev/null || true)"
    if ! open -n -a "Screen Sharing" "vnc://vnc:${vnc_password}@localhost:${vnc_port}"; then
        echo "Screen Sharing did not open; connect manually to localhost:${vnc_port}." >&2
        return
    fi

    for _ in {1..50}; do
        current_pid="$(pgrep -nx "Screen Sharing" 2>/dev/null || true)"
        if [[ -n "$current_pid" && "$current_pid" != "$previous_pid" ]]; then
            screen_sharing_pid="$current_pid"
            return
        fi
        sleep 0.1
    done

    echo "Could not isolate the test's Screen Sharing instance; leaving it open to avoid closing existing sessions." >&2
}

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [focused|all]" >&2
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

if ! command -v openssl >/dev/null 2>&1; then
    echo "OpenSSL is required to generate an ephemeral VNC password." >&2
    exit 1
fi

if ! podman info >/dev/null 2>&1; then
    echo "Podman is not running. On macOS, start its VM with 'podman machine start'." >&2
    exit 1
fi

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
vnc_password="$(openssl rand -hex 4)"

podman_args=(
    --platform linux/amd64
    --rm
    --userns=keep-id
    --volume "$repo_root:/workspace:rw"
    --workdir /workspace
    --env GRADLE_USER_HOME=/workspace/.gradle-podman
    --env FINTRAX_UI_TEST_FAILURE_SCREENSHOT_DIR=/workspace/ui/build/test-failures
    --env "JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=14 -XX:ActiveProcessorCount=2"
    --env "FINTRAX_UI_VNC_PASSWORD=$vnc_password"
)
podman_args+=(--publish "127.0.0.1:${vnc_port}:5900")

image="fintrax-ui-tests:local"
podman build \
    --platform linux/amd64 \
    --file "$repo_root/tools/ui-tests/Containerfile" \
    --tag "$image" \
    "$repo_root/tools/ui-tests"

podman run "${podman_args[@]}" "$image" \
    /usr/local/bin/run-ui-tests "$mode" --vnc &
container_pid=$!

vnc_ready=false
if command -v nc >/dev/null 2>&1; then
    for _ in {1..50}; do
        if ! kill -0 "$container_pid" 2>/dev/null; then
            break
        fi
        if nc -z -w 1 127.0.0.1 "$vnc_port" >/dev/null 2>&1; then
            vnc_ready=true
            break
        fi
        sleep 0.2
    done
fi

if [[ "$vnc_ready" == true ]]; then
    sleep 2
    if [[ "$(uname -s)" == "Darwin" ]]; then
        echo "Opening VNC at vnc://localhost:${vnc_port}"
        open_screen_sharing
    fi
else
    echo "VNC did not become ready on localhost:${vnc_port}; UI tests are still running." >&2
fi

set +e
wait "$container_pid"
test_status=$?
set -e
exit "$test_status"

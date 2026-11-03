#!/usr/bin/env bash
set -euo pipefail

mode="${1:-focused}"
shift || true

if [[ "$mode" == "focused" ]]; then
    test_args=(
        :ui:test
        --tests
        org.fintrax.ui.ClassificationsControllerTest.transactionFiltersStartCollapsedInClassificationsView
    )
elif [[ "$mode" == "all" ]]; then
    test_args=(:ui:test)
else
    echo "Unknown test mode '$mode'. Expected focused or all." >&2
    exit 2
fi

vnc_mode="${1:-}"
if [[ $# -gt 1 ]] || [[ -n "$vnc_mode" && "$vnc_mode" != "--vnc" ]]; then
    echo "Usage: run-ui-tests [focused|all] [--vnc]" >&2
    exit 2
fi

gradle_args=(
    --rerun-tasks
    --no-parallel
    --max-workers=1
    "-Dorg.gradle.jvmargs=-Xmx384m -Dfile.encoding=UTF-8"
    "${test_args[@]}"
)

xvfb_pid=""
vnc_pid=""
cleanup() {
    if [[ -n "$vnc_pid" ]]; then
        kill "$vnc_pid" 2>/dev/null || true
        wait "$vnc_pid" 2>/dev/null || true
    fi
    if [[ -n "$xvfb_pid" ]]; then
        kill "$xvfb_pid" 2>/dev/null || true
        wait "$xvfb_pid" 2>/dev/null || true
    fi
    rm -f /tmp/fintrax-ui-vnc.pass
}
trap cleanup EXIT

Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp -ac &
xvfb_pid=$!
export DISPLAY=:99
for _ in {1..50}; do
    if [[ -S /tmp/.X11-unix/X99 ]]; then
        break
    fi
    sleep 0.1
done
if ! kill -0 "$xvfb_pid" 2>/dev/null || [[ ! -S /tmp/.X11-unix/X99 ]]; then
    echo "Could not start the virtual display." >&2
    exit 1
fi

if [[ "$vnc_mode" == "--vnc" ]]; then
    vnc_password="$(openssl rand -hex 4)"
    x11vnc -storepasswd "$vnc_password" /tmp/fintrax-ui-vnc.pass >/dev/null
    x11vnc \
        -display :99 \
        -rfbport 5900 \
        -forever \
        -shared \
        -viewonly \
        -rfbauth /tmp/fintrax-ui-vnc.pass \
        -listen 0.0.0.0 \
        -quiet &
    vnc_pid=$!
    sleep 1
    if ! kill -0 "$vnc_pid" 2>/dev/null; then
        echo "Could not start the VNC server." >&2
        exit 1
    fi
    echo "View-only VNC: vnc://localhost:5900 (password: $vnc_password)"
fi

./gradlew "${gradle_args[@]}"

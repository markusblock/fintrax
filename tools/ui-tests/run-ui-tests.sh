#!/usr/bin/env bash
set -euo pipefail

mode="${1:-focused}"
shift || true
test_patterns=()
vnc_mode=""

usage() {
    echo "Usage: run-ui-tests [focused|all] [--vnc] | run-ui-tests test <pattern> [<pattern> ...] [--vnc]" >&2
}

case "$mode" in
    focused|all)
        if [[ $# -gt 1 ]] || [[ $# -eq 1 && "$1" != "--vnc" ]]; then
            usage
            exit 2
        fi
        vnc_mode="${1:-}"
        ;;
    test)
        while [[ $# -gt 0 && "$1" != "--vnc" ]]; do
            if [[ -z "$1" ]]; then
                echo "Test patterns must not be empty." >&2
                exit 2
            fi
            test_patterns+=("$1")
            shift
        done
        if [[ ${#test_patterns[@]} -eq 0 ]]; then
            usage
            exit 2
        fi
        if [[ $# -gt 1 ]] || [[ $# -eq 1 && "$1" != "--vnc" ]]; then
            usage
            exit 2
        fi
        vnc_mode="${1:-}"
        ;;
    *)
        echo "Unknown test mode '$mode'." >&2
        usage
        exit 2
        ;;
esac

case "$mode" in
    focused)
        test_args=(
            :ui:test
            --tests
            org.fintrax.ui.ClassificationsControllerTest.transactionFiltersStartCollapsedInClassificationsView
        )
        ;;
    all)
        test_args=(:ui:test)
        ;;
    test)
        test_args=(:ui:test)
        for pattern in "${test_patterns[@]}"; do
            test_args+=(--tests "$pattern")
        done
        ;;
esac

gradle_args=(
    --init-script tools/ui-tests/rerun-tests.gradle
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
    vnc_password="${FINTRAX_UI_VNC_PASSWORD:?VNC password must be supplied by scripts/ui-tests-podman.sh}"
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
    echo "View-only VNC password: $vnc_password"
fi

./gradlew "${gradle_args[@]}"

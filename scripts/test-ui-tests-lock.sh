#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fintrax-ui-lock.XXXXXX")"
lock_file="$temp_dir/ui-tests.lock"
ready_dir="$temp_dir/ready"
start_file="$temp_dir/start"
active_dir="$temp_dir/active"
mkdir -p "$ready_dir"
first_pid=""
second_pid=""

cleanup() {
    [[ -z "$first_pid" ]] || kill "$first_pid" 2>/dev/null || true
    [[ -z "$second_pid" ]] || kill "$second_pid" 2>/dev/null || true
    [[ -z "$first_pid" ]] || wait "$first_pid" 2>/dev/null || true
    [[ -z "$second_pid" ]] || wait "$second_pid" 2>/dev/null || true
    rm -rf "$temp_dir"
}
trap cleanup EXIT

run_locked_section() {
    trap - EXIT
    local name="$1"
    touch "$ready_dir/$name"
    while [[ ! -e "$start_file" ]]; do sleep 0.05; done
    bash "$repo_root/scripts/ui-tests-lock.sh" "$lock_file" bash -c '
        if [[ "${FINTRAX_UI_TEST_LOCK_OWNER_PID:-}" != "$$" ]]; then
            echo "UI test lock owner marker did not survive exec." >&2
            exit 1
        fi
        active_dir="$1"
        if ! mkdir "$active_dir" 2>/dev/null; then
            echo "UI test lock allowed overlapping runs." >&2
            exit 1
        fi
        sleep 0.5
        rmdir "$active_dir"
    ' bash "$active_dir"
}

run_locked_section first &
first_pid=$!
run_locked_section second &
second_pid=$!

ready=false
for attempt in {1..100}; do
    if [[ -e "$ready_dir/first" && -e "$ready_dir/second" ]]; then
        ready=true
        break
    fi
    sleep 0.05
done
if [[ "$ready" != true ]]; then
    echo "UI test lock smoke test could not start both workers." >&2
    exit 1
fi

touch "$start_file"
status=0
wait "$first_pid" || status=1
first_pid=""
wait "$second_pid" || status=1
second_pid=""
if [[ "$status" -ne 0 ]]; then
    exit "$status"
fi

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: ui-tests-lock.sh <lock-file> [--slots <slot-directory> <count>] <command> [arguments ...]" >&2
    exit 2
fi

lock_file="$1"
shift
slot_directory=""
slot_count=0
if [[ "${1:-}" == "--slots" ]]; then
    if [[ $# -lt 4 ]]; then
        echo "Usage: ui-tests-lock.sh <lock-file> --slots <slot-directory> <count> <command> [arguments ...]" >&2
        exit 2
    fi
    slot_directory="$2"
    slot_count="$3"
    if [[ ! "$slot_count" =~ ^[1-9][0-9]*$ ]]; then
        echo "Slot count must be a positive integer." >&2
        exit 2
    fi
    shift 3
fi
command_path="$1"
shift
if [[ "$command_path" != */* ]]; then
    command_path="$(command -v "$command_path")" || {
        echo "Command not found: $command_path" >&2
        exit 127
    }
fi
lock_directory="${lock_file%/*}"
if [[ "$lock_directory" == "$lock_file" ]]; then
    lock_directory="."
fi
mkdir -p "$lock_directory"
if [[ -n "$slot_directory" ]]; then
    mkdir -p "$slot_directory"
fi

if ! command -v perl >/dev/null 2>&1; then
    echo "Perl is required to serialize Podman UI test runs." >&2
    exit 1
fi

export FINTRAX_UI_TEST_LOCK_OWNER_PID="$$"
exec perl -e '
    use strict;
    use warnings;
    use Fcntl qw(LOCK_EX LOCK_NB F_SETFD);

    my ($lock_path, $slot_directory, $slot_count, $command) = splice @ARGV, 0, 4;
    open my $lock, ">>", $lock_path or die "Cannot open UI test lock $lock_path: $!\n";
    if (!flock($lock, LOCK_EX | LOCK_NB)) {
        print STDERR "Another UI test run is active in this worktree; waiting for it to finish.\n";
        flock($lock, LOCK_EX) or die "Cannot acquire UI test lock $lock_path: $!\n";
    }

    my $slot_lock;
    if ($slot_count > 0) {
        my $announced_wait = 0;
        while (!$slot_lock) {
            for my $index (1 .. $slot_count) {
                my $path = "$slot_directory/slot-$index.lock";
                open my $candidate, ">>", $path or die "Cannot open UI test slot $path: $!\n";
                if (flock($candidate, LOCK_EX | LOCK_NB)) {
                    $slot_lock = $candidate;
                    last;
                }
            }
            if (!$slot_lock) {
                if (!$announced_wait) {
                    print STDERR "All $slot_count Podman UI test slots are occupied; waiting for a slot.\n";
                    $announced_wait = 1;
                }
                select undef, undef, undef, 0.25;
            }
        }
    }

    # Keep worktree and capacity locks open through the runner and its Podman child.
    fcntl($lock, F_SETFD, 0) or die "Cannot preserve UI test lock across exec: $!\n";
    if ($slot_lock) {
        fcntl($slot_lock, F_SETFD, 0) or die "Cannot preserve UI test slot across exec: $!\n";
    }

    exec {$command} $command, @ARGV or die "Cannot run $command: $!\n";
' "$lock_file" "$slot_directory" "$slot_count" "$command_path" "$@"

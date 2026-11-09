#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: ui-tests-lock.sh <lock-file> <command> [arguments ...]" >&2
    exit 2
fi

lock_file="$1"
shift
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

if ! command -v perl >/dev/null 2>&1; then
    echo "Perl is required to serialize Podman UI test runs." >&2
    exit 1
fi

export FINTRAX_UI_TEST_LOCK_OWNER_PID="$$"
exec perl -e '
    use strict;
    use warnings;
    use Fcntl qw(LOCK_EX LOCK_NB F_SETFD);

    my $lock_path = shift @ARGV;
    my $command = shift @ARGV;
    open my $lock, ">>", $lock_path or die "Cannot open UI test lock $lock_path: $!\n";
    if (!flock($lock, LOCK_EX | LOCK_NB)) {
        print STDERR "Another Podman UI test run is active; waiting for it to finish.\n";
        flock($lock, LOCK_EX) or die "Cannot acquire UI test lock $lock_path: $!\n";
    }
    # The execed runner and its Podman child keep this descriptor open until the run finishes.
    fcntl($lock, F_SETFD, 0) or die "Cannot preserve UI test lock across exec: $!\n";

    exec {$command} $command, @ARGV or die "Cannot run $command: $!\n";
' "$lock_file" "$command_path" "$@"

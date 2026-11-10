# Local UI tests with Podman

Run TestFX UI tests in a Java 21 Linux container with a private Xvfb display. The container does not receive the host display, X11 socket, or input devices, so UI automation cannot compete with host desktop input.

## UI test scope

UI tests must verify user-visible behavior. Do not add tests whose sole purpose is to assert layout implementation details such as node positions, GridPane row/column assignments, sizing constraints, or parent relationships. When layout matters, verify an observable outcome—such as content remaining reachable after resizing or controls remaining usable—instead of locking tests to internal structure.

## Requirements

- Podman
- On macOS, a running Podman machine (`podman machine start`)
- Perl for the host-side cross-checkout lock
- Network access on the first run to build the image and resolve Gradle dependencies

## Commands

From the repository checkout, run a focused classification UI smoke test:

```sh
./scripts/ui-tests-podman.sh focused
```

Run one test method, a test class, or a package/class glob by passing one or more Gradle `--tests` patterns:

```sh
./scripts/ui-tests-podman.sh test org.fintrax.ui.RulesControllerTest.editingRuleFromContextMenuUpdatesRuleAndSelectedSummary
./scripts/ui-tests-podman.sh test org.fintrax.ui.RulesControllerTest
./scripts/ui-tests-podman.sh test 'org.fintrax.ui.controller.*'
```

Multiple patterns are allowed; each becomes a separate Gradle `--tests` filter. Quote shell globs so Gradle receives the pattern unchanged.

Run the full UI test suite:

```sh
./scripts/ui-tests-podman.sh all
```

Every test command starts an authenticated, view-only VNC server on the host loopback interface. The selected host port is printed. On macOS, the script automatically opens a separate Screen Sharing instance with the one-time password embedded in its connection URL, then closes only that instance when tests finish. Existing Screen Sharing windows remain open. The password is also printed for use with other VNC clients. `FINTRAX_UI_VNC_PORT` selects a fixed port (default `5900`); set it to `random` to let Podman choose an available host port and have the script discover it:

```sh
FINTRAX_UI_VNC_PORT=random ./scripts/ui-tests-podman.sh all
```

If automatic launch is unavailable, use the reported port in the manual connection. For example, when the reported port is `5900`, macOS users can run:

```sh
open vnc://localhost:5900
```

On other systems, connect with a VNC client to `localhost:<reported-port>`.

The focused mode runs `ClassificationsControllerTest.transactionFiltersStartCollapsedInClassificationsView`; `test` runs the supplied method/class/package patterns; `all` runs the complete UI suite. A Gradle init script forces Test tasks to execute on each invocation while leaving compilation outputs cached. Failed UI tests save a PNG of the Xvfb display under `ui/build/test-failures/`; screenshots are generated only by Podman runs and remain ignored build output. The script builds and runs a `linux/amd64` Java 21 image on a private Xvfb display, matching the architecture used by CI. On Apple Silicon, Podman emulates amd64; this is slower than native execution but avoids requesting OpenJFX `linux-aarch64` artifacts unavailable for the project's JavaFX 21.0.2. Gradle uses a 384 MiB daemon heap, a test-JVM heap capped at 14% of container memory, two reported processors, and one worker to fit the default 2 GiB Podman machine. Its user-home cache is kept in the checkout at `.gradle-podman/`; UI test reports and other build outputs remain in that checkout's normal `ui/build/` directory.

The wrapper acquires `~/.cache/fintrax/ui-tests-podman.lock` before building or starting a container. Concurrent wrapper invocations across worktrees and clones wait for the active run to finish; direct Podman or Gradle UI-test commands bypass this lock. Keep UI tests serial within one checkout because Gradle writes shared results under `ui/build/test-results/test`.

On macOS, Podman runs Linux containers in its VM. The host display is not forwarded and no host input devices are mounted. VNC exposes only the container's virtual display, not the macOS desktop. In Apple Silicon amd64 emulation, TestFX's `doubleClickOn` produced two single-click events rather than one double-click event, so `doubleClickingTransactionCategoryOpensCategoryPicker` times out locally. Main's native-amd64 CI passed this test. First image/dependency downloads can take several minutes.

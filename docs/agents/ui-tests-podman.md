# Local UI tests with Podman

Run TestFX UI tests in a Java 21 Linux container with a private Xvfb display. The container does not receive the host display, X11 socket, or input devices, so UI automation cannot compete with host desktop input.

## Requirements

- Podman
- On macOS, a running Podman machine (`podman machine start`)
- Network access on the first run to build the image and resolve Gradle dependencies

## Commands

From the repository checkout, run a focused classification UI smoke test:

```sh
./scripts/ui-tests-podman.sh focused
```

Run the full UI test suite:

```sh
./scripts/ui-tests-podman.sh all
```

Every test command starts an authenticated, view-only VNC server on the host loopback interface. On macOS, the script automatically opens a separate Screen Sharing instance with the one-time password embedded in its connection URL, then closes only that instance when tests finish. Existing Screen Sharing windows remain open. The password is also printed for use with other VNC clients. Set `FINTRAX_UI_VNC_PORT` to select another local port if `5900` is occupied. If automatic launch is unavailable, macOS users can open it manually:

```sh
open vnc://localhost:5900
```

On other systems, connect with a VNC client to `localhost:5900`.

The focused mode runs `ClassificationsControllerTest.transactionFiltersStartCollapsedInClassificationsView`; `all` runs the complete UI suite. A Gradle init script forces Test tasks to execute on each invocation while leaving compilation outputs cached. Failed UI tests save a PNG of the Xvfb display under `ui/build/test-failures/`; screenshots are generated only by Podman runs and remain ignored build output. The script builds and runs a `linux/amd64` Java 21 image on a private Xvfb display, matching the architecture used by CI. On Apple Silicon, Podman emulates amd64; this is slower than native execution but avoids requesting OpenJFX `linux-aarch64` artifacts unavailable for the project's JavaFX 21.0.2. Gradle uses a 384 MiB daemon heap, a test-JVM heap capped at 14% of container memory, two reported processors, and one worker to fit the default 2 GiB Podman machine. Its user-home cache is kept in the checkout at `.gradle-podman/`; UI test reports and other build outputs remain in that checkout's normal `ui/build/` directory.

Do not run UI test commands concurrently against the same checkout: Gradle writes shared test-result files under `ui/build/test-results/test`. Separate Git worktrees have separate build directories and can be tested independently.

On macOS, Podman runs Linux containers in its VM. The host display is not forwarded and no host input devices are mounted. VNC exposes only the container's virtual display, not the macOS desktop. In Apple Silicon amd64 emulation, TestFX's `doubleClickOn` produced two single-click events rather than one double-click event, so `doubleClickingTransactionCategoryOpensCategoryPicker` times out locally. Main's native-amd64 CI passed this test. First image/dependency downloads can take several minutes.

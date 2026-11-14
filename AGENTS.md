# Fintrax

## Agent skills

### Issue tracker

Issues and PRDs for this repo live as GitHub issues, managed via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Issues use the default five triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Eclipse Store data model

- Use the default `fintrax.storage.path` by default. Set a worktree-specific path only to reproduce or isolate storage compatibility issues; do not share one writable store between incompatible model revisions.
- Before changing persisted types, check [Eclipse Store legacy type mapping](https://docs.eclipsestore.io/manual/storage/legacy-type-mapping/index.html); do not assume class renames or semantic conversions map safely.
- Account for defaults on old records when adding fields (`null`, `0`, `false`); backfill when domain defaults differ.
- Keep renamed or removed classes available until stored references are migrated. `@Deprecated` does not migrate stored data.
- Configure explicit mappings or custom legacy handlers before `EmbeddedStorage.start`; mark a type deleted only after verifying its instances are unreachable.
- Back up before migrations. Test with a fixture written by the previous model version; verify migrated values and a subsequent restart.

## Testing

Run Gradle/TestFX checks serially; separate Gradle test processes can overwrite shared `ui/build/test-results/test` XML. Combine `--tests` filters into one invocation when possible.

For changes under `ui/` (Java, FXML, or tests), read `docs/agents/ui-tests-podman.md` and run `./scripts/ui-tests-podman.sh all` before publishing. Use `./scripts/ui-tests-podman.sh test <Gradle --tests pattern> [<pattern> ...]` for focused method, class, or package tests; multiple patterns are allowed, and shell globs must be quoted. Host-only TestFX runs are not a substitute. If Podman cannot run, state why and require passing Linux/Xvfb CI; see the UI test guide for the Apple Silicon emulation caveat.

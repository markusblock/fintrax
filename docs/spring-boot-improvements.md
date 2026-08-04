# Deferred improvement backlog

These items are intentionally outside the behavior-preserving Spring Boot migration.

## High priority

- Fix `SyncService.nextId`, which is a mutable non-atomic counter despite concurrent sync execution being possible. Add a concurrent-sync regression test before choosing between serialized sync execution, an atomic counter, or storage-backed ID allocation.
- Add explicit application-service tests that do not require JavaFX or global process state.
- Define transaction and consistency boundaries for multi-step operations such as sync, import, and data reset.

## Architecture

- Replace direct `StoreManager` dependencies with persistence ports or repositories.
- Keep `DataRoot` private to the persistence adapter instead of exposing mutable storage state to services.
- Separate use-case orchestration from JavaFX controllers.
- Define a banking port and keep `FinTsAdapter` as an infrastructure adapter.
- Replace direct system-property access and path resolution with typed configuration and an injected storage-location abstraction.
- Add a dedicated navigation abstraction if view lifecycle and state become more complex.

## Persistence and platform

- Evaluate SQL persistence only if multi-user access, cross-process transactions, reporting, or operational requirements justify the migration cost.
- Reassess Spring Boot versus Spring Framework if the application continues to need only dependency injection and lifecycle management without web or platform features.
- Add web/API capabilities only when a concrete client or deployment requirement exists.

# Spring Boot migration

## Scope

Migrate the existing JavaFX desktop application to an embedded, non-web Spring Boot context. Preserve current behavior, user experience, EclipseStore data format, FinTS integration, localization, settings, import behavior, reset behavior, and Gradle module boundaries.

Explicit non-goals:

- Web UI or REST API
- Remote or multi-user deployment
- Actuator or security infrastructure
- JPA/Hibernate or SQL persistence
- Domain redesign
- Concurrency fixes unrelated to Spring ownership

## Sequence

1. Pin a Java 21-compatible Spring Boot release and add dependency management to the root Gradle build.
2. Add non-web Spring bootstrap and JavaFX-owned context lifecycle.
3. Register EclipseStore, storage configuration, PIN storage, banking adapter, and other infrastructure as explicit beans.
4. Convert application services to Spring-managed singleton beans.
5. Add prototype-scoped controllers and a Spring-managed `ViewLoader`.
6. Configure all FXML loading through `ViewLoader`, including the main window, navigation views, and add-account dialog.
7. Migrate JavaFX controllers from `ServiceRegistry` access to constructor injection.
8. Migrate controller groups in risk order: startup and bank accounts, transactions, categories/labels/rules, settings/import/reset, then activity log.
9. Remove `ServiceRegistry` and legacy startup/shutdown wiring.
10. Run compatibility and regression gates before accepting the migration.

## Verification gates

- Spring context starts with all required beans and no legacy registry.
- Spring context closes and `StoreManager.shutdown()` runs.
- Every FXML view loads through the Spring controller factory.
- Existing JavaFX navigation and UI tests pass.
- A fixture created by the pre-migration application remains readable.
- Representative reads, writes, reset, import, settings, and sync behavior remain equivalent.
- The fixture remains readable after restart.
- Startup failure exits without opening a partially initialized UI or silently changing storage location.

The compatibility gate uses a synthetic EclipseStore data set shaped by the pre-migration model. No real production data is available for testing. Sync coverage verifies Spring wiring of `SyncService`, `BankingProtocol`, `PinStorage`, and `StoreManager`; it does not execute a live banking synchronization.

## Bean policy

- `@Service` for application services.
- `@Component` for controllers and `ViewLoader`.
- `@Configuration` and explicit `@Bean` methods for infrastructure and adapters.
- Prototype scope for controllers; singleton scope for services and infrastructure.
- Spring configuration properties for runtime/infrastructure settings.
- `SettingsService` for persisted user settings.

# Embedded Spring Boot context for the JavaFX application

Status: accepted

Fintrax will keep its JavaFX desktop UI and current Gradle multi-project structure while introducing Spring Boot as an embedded, non-web application context. JavaFX owns process and toolkit lifecycle; Spring owns the complete object graph, including services and infrastructure, and the legacy static `ServiceRegistry` is removed after consumers migrate. The first migration retains EclipseStore, persisted data formats, user-visible behavior, and existing integrations so framework adoption is not coupled to a persistence or domain rewrite.

## Consequences

- Controllers are prototype-scoped Spring beans created through a shared Spring-managed `ViewLoader` and constructor injection.
- Application services are singleton-scoped Spring beans; infrastructure is registered through explicit `@Bean` configuration with managed shutdown.
- Spring configuration owns infrastructure settings such as the storage path; `SettingsService` remains the source of persisted user preferences.
- The first migration adds no web server, REST API, Actuator, security subsystem, or database migration.
- Spring context startup failure is fatal. There is no fallback to `ServiceRegistry` or a mixed legacy/Spring object graph.
- The exact latest stable Spring Boot release compatible with Java 21 is pinned when implementation begins.

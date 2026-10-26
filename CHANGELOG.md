# Changelog

Notable changes to Rose Audit Spring Boot Starter.

## 0.1.0-SNAPSHOT

- Require Java 25 and Spring Boot 4.1.2. Publish signed releases through the `release` profile.
- Record Servlet requests once across MVC routing, Security short circuits, and asynchronous completion. MVC actions use `METHOD /route/template`; scheduled and message listeners use `SimpleClassName.methodName`.
- Record standard Spring Security authentication events and provide `AuditAuthenticationRecorder` for custom flows. Keep authenticated account IDs separate from display names; never treat an unverified login name as the actor.
- Add event IDs, event types, `UNKNOWN` outcomes, occurrence time, duration, and optional HTTP metadata. Remove `targetType`, `targetId`, and the general-purpose `@AuditLog` aspect.
- Replace the `AuditEventPublisher` extension point with Spring application events and add `AuditEventConsumer` for custom destinations. Events use a bounded, non-durable asynchronous queue with no retries and are independent of business transaction outcomes.
- Add JDBC persistence and paginated search with Spring Data `Pageable`/`Page`. Writes use a new transaction; applications must apply `audit-schema-postgresql.sql` to new databases or `audit-schema-postgresql-v2.sql` to existing ones before upgrading. Mixed-version rolling upgrades are unsupported.
- Add `rose.audit.enabled`, query-parameter allowlisting, opt-in error-message capture, schema validation, and optional Micrometer metrics. Request bodies, headers, and full method arguments or return values are not captured by default.
- Log publishing and persistence failures without changing the business result. Preserve request correlation and logout display names when the Security context is cleared.
- Add a PostgreSQL-backed sample application and Testcontainers integration tests.

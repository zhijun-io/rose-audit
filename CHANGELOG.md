# Changelog

Notable changes to Rose Audit Spring Boot Starter are listed here.

## 0.1.0-SNAPSHOT

- Sign and publish Maven Central releases only when the `release` profile is active. Snapshot deploys stay unsigned.
- Keep `actorId` and `actorName` distinct. Spring Security supplies the authenticated display name; an unverified login
  name is not stored as the actor. The account id stays empty unless the application provides it.
- Add `rose.audit.enabled` so recording can be turned off without removing JDBC search.
- Audit Servlet requests once across MVC, Security short circuits, and asynchronous completion. MVC contributes handler
  templates; the advisor remains for scheduled and message entry points.
- Omit query strings unless individual parameter names are added to `rose.audit.http.query-parameters-allowlist`.
- Fail startup when the built-in JDBC store cannot query `audit_event`, unless `rose.audit.jdbc.validate-schema` is
  disabled. Add an index on `action` and occurrence time.
- Persist execution-fact events independently of the business transaction outcome.
- Move the event model and context-provider APIs to `io.zhijun.audit`, and the servlet request-context provider to
  `io.zhijun.audit.web`; HTTP status is captured when the request completes.
- Replace the public `AuditEventPublisher` SPI with Spring application events. Applications that implemented the SPI should migrate to `@EventListener`.
- Add `AuditEventConsumer` as the extension point for custom consumers such as MQ publishers; the default JDBC consumer backs off when an application provides one.
- Automatically audit mapped MVC requests using `METHOD /route/template` actions and scheduled/message-listener entry
  methods using `SimpleClassName.methodName` actions. Scheduled and message-listener entry points are detected from `@Scheduled`,
  `@KafkaListener`, `@RabbitListener`, and `@JmsListener`; internal service methods are not audited automatically.
- Document AOP proxy and asynchronous Controller boundaries, and clarify that replacing the consumer does not make the
  in-memory delivery queue durable.
- Remove `targetType` and `targetId` from audit events and JDBC searches because automatic entry points did not have a
  reliable source for these values.
- Remove the general-purpose `@AuditLog` annotation and its aspect to keep auditing at entry points.
- Record `occurredAt`, `durationMs`, error type, HTTP method, route, query string, status, client IP, and
  user-agent fields where available. Do not capture headers, bodies, or complete method arguments/return values by
  default. Exception messages are omitted by default and can be enabled with `rose.audit.error.capture-message=true`.
- Change the `AuditEvent` and PostgreSQL schema fields to use `occurredAt` and `durationMs`, and add optional HTTP
  request metadata fields.
- Add asynchronous event consumption through a bounded executor. Execution facts are queued immediately, independent of
  business transaction outcome. The in-memory queue is not durable.
- Add optional Micrometer counters and consumer-duration timing when a `MeterRegistry` is present.
- Add transactional JDBC persistence and paginated query APIs. JDBC records event type and ID; duration may be unknown.
  Existing tables require `classpath:db/audit-schema-postgresql-v2.sql` before new code writes events. Stop the
  previous version before migrating; mixed rolling upgrade is not supported. The script is outside Flyway's default
  `db/migration` path.
- An included logout request keeps the authenticated display name after the security context is cleared.
- Keep a committed HTTP 200 when a later transfer failure marks the request failed. An uncommitted default 200 stays
  empty. A failed context capture keeps the request id and increments the build-failure count. Bounded text fields
  drop other control characters.
- Use Spring Data `Pageable`/`Page` for offset pagination; cursor pagination is not part of the query API.
- Add `AuditEventService.create(AuditEvent)` and route the JDBC event listener through the service.
- Log audit publishing and persistence failures without changing the business result or exception.
- Add a PostgreSQL schema example and a PostgreSQL-backed sample application with a web demonstration.
- Add PostgreSQL Testcontainers integration tests for persistence and pagination.
- Require Java 25 and Spring Boot 4.1.x.

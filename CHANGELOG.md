# Changelog

Notable changes to Rose Audit Spring Boot Starter are listed here.

## 0.1.0-SNAPSHOT

- Sign and publish Maven Central releases only when the `release` profile is active. Snapshot deploys stay unsigned.
- Keep `actorId` and `actorName` distinct. Spring Security and failed logins supply the display name only; the account
  id stays empty unless the application provides it. Anonymous sessions are not stored as actors.
- Add `rose.audit.enabled` so recording can be turned off without removing JDBC search.
- Audit Spring MVC request mappings only, and skip `ErrorController` types so an error dispatch is not stored as a
  success.
- Omit HTTP query strings unless `rose.audit.http.capture-query-string` is enabled.
- Fail startup when the built-in JDBC store cannot query `audit_event`, unless `rose.audit.jdbc.validate-schema` is
  disabled. Add an index on `action` and occurrence time.
- Run the entry-point advisor inside the transaction advisor so a committed `REQUIRES_NEW` method is still recorded
  when its caller rolls back.
- Move the event model and context-provider APIs to `io.zhijun.audit`, and the servlet request-context provider to
  `io.zhijun.audit.web`; Controller AOP events no longer record a possibly premature default HTTP status.
- Replace the public `AuditEventPublisher` SPI with Spring application events. Applications that implemented the SPI should migrate to `@EventListener`.
- Add `AuditEventConsumer` as the extension point for custom consumers such as MQ publishers; the default JDBC consumer backs off when an application provides one.
- Automatically audit Spring MVC Controller handler methods and scheduled/message-listener entry methods using
  `SimpleClassName.methodName` actions. Scheduled and message-listener entry points are detected from `@Scheduled`,
  `@KafkaListener`, `@RabbitListener`, and `@JmsListener`; internal service methods are not audited automatically.
- Document AOP proxy and asynchronous Controller boundaries, and clarify that replacing the consumer does not make the
  in-memory delivery queue durable.
- Remove `targetType` and `targetId` from audit events and JDBC searches because automatic entry points did not have a
  reliable source for these values.
- Remove the general-purpose `@AuditLog` annotation and its aspect to keep auditing at entry points.
- Record `occurredAt`, `durationMs`, error type/message, HTTP method, route, query string, status, client IP, and
  user-agent fields where available. Do not capture headers, bodies, or complete method arguments/return values by
  default.
- Change the `AuditEvent` and PostgreSQL schema fields to use `occurredAt` and `durationMs`, and add optional HTTP
  request metadata fields.
- Add asynchronous event consumption through a bounded executor. Successful events are queued after transaction commit;
  failure events are queued after transaction completion. The in-memory queue is not durable.
- Add optional Micrometer counters and consumer-duration timing when a `MeterRegistry` is present.
- Add transactional JDBC persistence and paginated query APIs. A `SUCCESS` event is persisted only after the associated
  transaction commits; a `FAILURE` event is persisted after the transaction completes, including rollback.
- Use Spring Data `Pageable`/`Page` for offset pagination; cursor pagination is not part of the query API.
- Add `AuditEventService.create(AuditEvent)` and route the JDBC event listener through the service.
- Log audit publishing and persistence failures without changing the business result or exception.
- Add a PostgreSQL schema example and a PostgreSQL-backed sample application with a web demonstration.
- Add PostgreSQL Testcontainers integration tests for persistence and pagination.
- Require Java 25 and Spring Boot 4.1.x.

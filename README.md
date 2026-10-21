# Rose Audit Spring Boot Starter

Rose Audit is a Spring Boot starter for recording application audit events. It automatically audits Spring MVC
Controller handler methods through AOP and lets authentication filters explicitly record events they complete before a
Controller is invoked. Events are published through Spring's application event system. The project includes a JDBC
consumer, paginated event queries, and a runnable sample with a browser-based demonstration.

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Features

- Record successful and failed Controller handler outcomes without adding annotations to each method. Actions use the
  `SimpleControllerName.methodName` format.
- Record public scheduled-job and message-listener methods marked with Spring's `@Scheduled`, `@KafkaListener`,
  `@RabbitListener`, or `@JmsListener` annotations. Actions use the `SimpleClassName.methodName` format. Add these
  annotations only to entry methods; called services are not audited automatically.
- Capture actor, tenant, request, trace, HTTP request, client IP, and user-agent metadata through context providers.
- Record authentication events from filter-based login flows.
- Record filter-based operations explicitly through the authentication recorder or Spring application events.
- Publish events for application listeners and consume audit events asynchronously after the associated transaction completes.
- Persist events with JDBC and query them with Spring Data pagination; applications can provide another consumer, such as an MQ publisher.
- Run the sample application and explore audit events in its web interface.

## Operational notes

- AOP records a Controller handler when the proxied method returns or throws. It does not observe completion or failure
  of asynchronous return values such as `CompletableFuture`, `Callable`, or reactive publishers; those methods are
  currently recorded as successful when they return the async value. AOP also requires Spring-managed, proxyable public
  entry methods. Final classes/methods, objects created outside Spring, and internal self-invocation are not audited.
- Audit event delivery uses an in-memory queue. The starter waits up to 30 seconds for queued work during an orderly
  Spring application shutdown, but process crashes, forced termination, queue rejection, or consumer failures can still
  lose events. Replacing the consumer does not remove this in-memory queue or guarantee delivery. Use a durable delivery
  design when audit-event loss is unacceptable.
- When Micrometer and an application `MeterRegistry` are available, the starter records queued, consumed, and failed
  event counts and consumer duration. Spring Boot provides its own executor pool and queue metrics when its metrics
  instrumentation is enabled. The starter does not add Actuator or configure a metrics exporter; applications can use
  their existing registry and monitoring setup. Audit meters are `rose.audit.events.queued`,
  `rose.audit.events.consumed`, `rose.audit.events.failures` (tagged by failure stage and outcome), and
  `rose.audit.consumer.duration` (tagged by outcome).
- JDBC queries do not apply tenant authorization automatically. Applications must enforce the caller's tenant scope and
  access permissions when exposing audit-event searches.
- Audit events can contain personal or correlatable data, including actor identifiers, client IP, user-agent, and raw
  query strings. Applications should collect only what they need, sanitize request context in their
  `AuditRequestContextProvider`, and apply suitable access and retention controls. The starter does not capture request
  bodies or credentials from headers by default.
- The servlet provider reads `HttpServletRequest.getRemoteAddr()`. When the application is behind a reverse proxy,
  configure Spring Boot forwarded-header handling (for example, `server.forward-headers-strategy: NATIVE` or
  `FRAMEWORK`) and ensure only trusted proxies can supply forwarded headers. Otherwise, the recorded address may be the
  proxy address or may be based on untrusted client-supplied headers.

## Modules

- `rose-audit-spring-boot-starter`: dependency entry point for applications.
- `rose-audit-spring-boot-autoconfigure`: audit event model, AOP integration, and auto-configuration.
- `rose-audit-spring-boot-jdbc`: JDBC persistence and paginated query API.
- `rose-audit-sample`: runnable application and frontend demonstration.

## Requirements and technology stack

- Java 25
- Spring Boot 4.1.x
- Maven
- PostgreSQL for the JDBC sample and PostgreSQL integration tests
- Docker for Testcontainers integration tests

The audit event records `occurredAt`, `durationMs`, outcome, actor and action identifiers, error type/message, request
and trace identifiers, and optional HTTP method, route, query string, status, client IP, and user agent. It does not
capture request/response headers, request bodies, or full method arguments/return values by default. Non-HTTP events
leave HTTP-specific fields empty. The servlet provider leaves `httpStatus` empty for Controller AOP events because the
final status may be applied after the Controller method returns. Filter-based authentication events receive their action
identifier from the caller.

The starter uses Spring AOP, Spring application events, Spring JDBC, and Spring Data's `Pageable`/`Page` API. It does not
require Spring Security or the Servlet API for the core audit functionality.

Kafka, RabbitMQ, and JMS listener annotations are detected by name. Their Spring integration dependencies remain the
application's responsibility; the starter does not add them.

## Run the sample

Start PostgreSQL, build the sample, and run it:

```sh
docker compose -f rose-audit-sample/compose.yaml up -d --wait postgres
mvn -pl rose-audit-sample -am package
java -jar rose-audit-sample/target/rose-audit-sample-0.1.0-SNAPSHOT.jar
```

Open <http://localhost:8080> to create and browse sample audit events. PostgreSQL is exposed on port `55432` by default;
set `AUDIT_DB_PORT` to use a different host port.

## Tests and formatting

Run the Maven test suite from the project root:

```sh
mvn test
```

The JDBC integration tests use PostgreSQL through Testcontainers and require Docker. Check Java formatting with:

```sh
mvn spotless:check
```

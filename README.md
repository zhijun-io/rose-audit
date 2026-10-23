# Rose Audit Spring Boot Starter

[![CI](https://github.com/zhijun-io/rose-audit-spring-boot-starter/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/zhijun-io/rose-audit-spring-boot-starter/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
[![GitHub stars](https://img.shields.io/github/stars/zhijun-io/rose-audit-spring-boot-starter)](https://github.com/zhijun-io/rose-audit-spring-boot-starter/stargazers)
[![License](https://img.shields.io/github/license/zhijun-io/rose-audit-spring-boot-starter)](LICENSE)

Rose Audit is a Spring Boot starter for recording and querying application audit events. It includes automatic auditing
for Spring MVC controllers and common scheduled or messaging entry points, JDBC persistence, paginated queries, and a
runnable sample application.

## Features

- Record completed Spring MVC requests with `METHOD /route/template` action names.
- Record scheduled jobs and Kafka, RabbitMQ, or JMS listener entry points.
- Record Spring Security authentication success and failure events when those events are published. Use
  `AuditAuthenticationRecorder` for custom flows that do not publish standard events.
- Capture actor, tenant, request and trace identifiers, operation duration, and available HTTP request metadata.
- Consume observed execution facts asynchronously, regardless of business transaction rollback. Use the included JDBC consumer or
  provide a consumer for another destination such as a message queue.
- Store and search events with JDBC and Spring Data pagination.
- Expose optional Micrometer metrics and a browser-based sample interface.

The starter does not capture request bodies, headers, query strings, or full method arguments and return values by
default. Query parameters can only be captured by an explicit allowlist.

## Requirements and technology

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL for the sample application and JDBC integration tests
- Docker for the sample database and Testcontainers integration tests

Maven coordinates: `io.zhijun:rose-audit-spring-boot-starter`.

## Configuration

```yaml
rose:
  audit:
    enabled: true
    error:
      capture-message: false
    http:
      include:
        - /auth/**
      query-parameters-allowlist: []
    jdbc:
      validate-schema: true
```

`enabled=false` stops recording and leaves JDBC search in place. By default, only mapped MVC controller requests are
recorded. `http.include` adds matching non-controller endpoints; default exclusions win over includes. Configure
`http.exclude` to replace the default exclusion list. Query values are stored only for names in
`http.query-parameters-allowlist`. Apply `classpath:db/audit-schema-postgresql.sql` to a new database. Existing tables
must apply `classpath:db/audit-schema-postgresql-v2.sql` before the new writer starts. The script lives outside
Flyway's `db/migration` path, so adding this library does not change the schema by itself. It marks legacy event
types as `UNKNOWN` and makes duration nullable. Stop the previous version first: it cannot read those values, and a
mixed rolling upgrade is not supported.
The previous `rose.audit.http.capture-query-string` setting is unsupported; use the explicit query parameter allowlist.
The new `AuditEvent` constructor requires event ID and type; update consumers that construct events directly and
exhaustive `Outcome` switches.
Exception messages are omitted by default because they may contain sensitive values. Set
`rose.audit.error.capture-message` to `true` only after reviewing the messages produced by your application.

`actorId` is the account id and `actorName` is the display name. Without an `AuditActorProvider`, Spring Security fills
only `actorName` from the authenticated principal name when an MVC handler runs. An included logout request keeps that
name from the standard logout event after the security context is cleared. Anonymous sessions and non-HTTP work have
neither. A failed login never stores the unverified submitted username as the authenticated actor. Avoid manually
recording a flow that already publishes standard Spring Security authentication events, or the attempt will be
recorded twice. Search is not limited to the current tenant.

The default queue holds 200 events, does not retry, and drops overflow. Supply an `AuditEventConsumer` when loss is
unacceptable, and keep `auditEventTaskExecutor` asynchronous. Controller requests are measured through Servlet
completion, including asynchronous MVC completion; method return is not treated as request completion. Scheduler and
message listener calls are recorded at the intercepted method boundary. Client IP is the servlet remote address. The
sample listens on `127.0.0.1` and has no authentication.

## Run the sample

Start the PostgreSQL container, build the sample, and run it:

```sh
docker compose -f rose-audit-sample/compose.yaml up -d --wait postgres
mvn -pl rose-audit-sample -am package
java -jar rose-audit-sample/target/rose-audit-sample-0.1.0-SNAPSHOT.jar
```

Open <http://localhost:8080> to create and browse sample audit events. PostgreSQL is exposed on port `55432` by default;
set `AUDIT_DB_PORT` to use another host port.

## Test and format

Run the Maven test suite from the project root:

```sh
mvn test
```

The JDBC integration tests use PostgreSQL through Testcontainers and require Docker. Check Java formatting with:

```sh
mvn spotless:check
```

## License

[Apache License 2.0](LICENSE).

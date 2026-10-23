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

- Record successful and failed controller operations with `ControllerName.methodName` action names.
- Record scheduled jobs and Kafka, RabbitMQ, or JMS listener entry points.
- Record authentication events from filter-based login flows.
- Capture actor, tenant, request and trace identifiers, operation duration, and available HTTP request metadata.
- Consume audit events asynchronously after the business transaction completes. Use the included JDBC consumer or
  provide a consumer for another destination such as a message queue.
- Store and search events with JDBC and Spring Data pagination.
- Expose optional Micrometer metrics and a browser-based sample interface.

The starter does not capture request bodies, headers, query strings, or full method arguments and return values by
default.

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
    http:
      capture-query-string: false
```

`enabled=false` stops recording and leaves JDBC search in place. Query strings are stored only when
`capture-query-string` is `true`.

`actorId` is the account id and `actorName` is the display name. Without an `AuditActorProvider`, Spring Security fills
only `actorName` from the authenticated principal name. Anonymous sessions and non-HTTP work have neither. A failed
login stores the submitted username as `actorName` and leaves `actorId` empty. Call `AuditAuthenticationRecorder` from
login handlers. `ErrorController` types are skipped.

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

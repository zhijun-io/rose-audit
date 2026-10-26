# Rose Audit

[![CI](https://github.com/zhijun-io/rose-audit/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/zhijun-io/rose-audit/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)
![Spring Boot 4.1.2](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)
![Coverage: 80% minimum](https://img.shields.io/badge/coverage-80%25%20minimum-brightgreen)
[![License](https://img.shields.io/github/license/zhijun-io/rose-audit)](LICENSE)

Rose Audit records application activity at HTTP, authentication, scheduled-task, and message-listener boundaries. Events are delivered asynchronously to a consumer; the JDBC module provides PostgreSQL persistence and paginated search.

See [architecture and implementation scope](docs/architecture.md) for module responsibilities, event semantics, delivery limits, and schema details.

## Add the starter

The aggregate starter includes auto-configuration and JDBC persistence:

```xml
<dependency>
    <groupId>io.zhijun</groupId>
    <artifactId>rose-audit-spring-boot-starter</artifactId>
    <version>${rose-audit.version}</version>
</dependency>
```

Requires Java 25 and Spring Boot 4.1.2. The JDBC auto-configuration uses the application's `DataSource` and expects the `audit_event` table to exist.

## Configure

```yaml
rose:
  audit:
    enabled: true
    error:
      capture-message: false
    http:
      include: []
      exclude: [] # Empty replaces the default exclusions
      query-parameters-allowlist: []
    jdbc:
      validate-schema: true
```

Audit is enabled by default. Mapped MVC controller requests are recorded automatically; `http.include` adds other paths. Exclusions take precedence over includes. Setting `http.exclude` replaces the defaults, which exclude health, documentation, static-resource, and error paths.

Request bodies, headers, method arguments, and return values are not captured. Query values are captured only for names in `query-parameters-allowlist`. Exception messages are disabled by default because they may contain sensitive data.

Without an `AuditActorProvider`, Spring Security supplies the authenticated principal name when available; provide a custom provider for actor IDs or tenant context. Standard Spring Security authentication events are recorded when published. Use `AuditAuthenticationRecorder` for custom login flows that do not publish them, and avoid recording the same flow twice.

## Database schema

Apply `classpath:db/audit-schema-postgresql.sql` when creating a new database. For a database created with the previous schema, stop the old application version and apply `classpath:db/audit-schema-postgresql-v2.sql` before starting this version. The migration is not run automatically; mixed old and new writers are unsupported.

With the default JDBC consumer, startup validates that the current `audit_event` schema can be queried. Set `rose.audit.jdbc.validate-schema=false` to disable this check.

## Run the sample

The sample requires Docker for PostgreSQL. From the repository root:

```sh
docker compose -f rose-audit-sample/compose.yaml up -d --wait postgres
mvn -pl rose-audit-sample -am package
java -jar rose-audit-sample/target/rose-audit-sample-0.1.0-SNAPSHOT.jar
```

Open <http://localhost:8080>. The sample binds to `127.0.0.1` and has no authentication. PostgreSQL uses host port `55432`; set `AUDIT_DB_PORT` to change it.

## Build and verify

Run the Maven verification lifecycle from the repository root:

```sh
mvn verify
mvn spotless:check
```

JaCoCo reports are written to each module's `target/site/jacoco/`. Verification enforces at least 80% line coverage for library modules; the sample is reported without a threshold. JDBC integration tests use Testcontainers and require Docker.

## License

[Apache License 2.0](LICENSE).

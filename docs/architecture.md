# Rose Audit 架构与实现范围

Rose Audit 在 Spring Boot 应用的请求、认证和后台入口处采集审计事实，并通过异步 Consumer 投递。事件表示“观察到的执行结果”，不推断业务事务是否提交。

## 模块

| 模块 | 职责 |
| --- | --- |
| `rose-audit-spring-boot-autoconfigure` | 事件 API、上下文 Provider、HTTP/认证/后台入口适配、异步投递自动配置 |
| `rose-audit-spring-boot-starter-jdbc` | JDBC Consumer、分页查询、PostgreSQL schema |
| `rose-audit-spring-boot-starter` | 聚合默认依赖 |
| `rose-audit-sample` | 可运行示例 |

## 事件来源

- Servlet Filter 覆盖 HTTP 请求生命周期；MVC 拦截器补充 `METHOD /route/template`。异步完成、超时和错误分派共用请求状态，每个请求最多提交一次事件。
- Spring Security 发布的认证成功/失败事件可自动记录。自定义登录流程使用 `AuditAuthenticationRecorder`；同一流程只使用一种记录来源。
- `@Scheduled`、`@KafkaListener`、`@RabbitListener` 和 `@JmsListener` 标记的方法按入口执行结果记录。内部服务调用不自动记录。

事件包含唯一 `eventId`、事件类型、结果、发生时间以及可用的请求、身份和租户上下文。`requestId` 用于关联同一请求内的事件；未知结果和未知耗时不伪装成成功或零值。

## HTTP 范围与隐私

默认记录映射到业务 Controller 的请求。`rose.audit.http.include` 可纳入其他端点，`exclude` 控制排除规则，排除优先。默认排除健康检查、静态资源、文档端点和 CORS 预检。

默认不采集请求体、请求头、方法参数或返回值。查询参数必须通过 `query-parameters-allowlist` 指定；异常消息默认关闭。应用负责为其自定义 Provider 提供可信且适当脱敏的身份和租户值。

## 投递与持久化

事件经 Spring application event 投递给异步 `AuditEventConsumer`。默认队列容量为 200，不重试；队列拒绝、消费失败、进程崩溃或关闭超时都可能丢失事件。自定义 Consumer 可替换 JDBC Consumer，但 JDBC 查询服务仍可用。

配置 DataSource 后，JDBC 模块默认写入 `audit_event`，单次写入使用独立事务。Starter 不自动创建或迁移表：新库执行 `db/audit-schema-postgresql.sql`；旧表先执行 `db/audit-schema-postgresql-v2.sql`，再启动新版本。混合新旧写入端不受支持。

详细配置和示例命令见 [README](../README.md)，版本变化见 [CHANGELOG](../CHANGELOG.md)。

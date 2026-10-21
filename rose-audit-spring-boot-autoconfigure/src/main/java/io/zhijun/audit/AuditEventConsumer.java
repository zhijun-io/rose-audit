package io.zhijun.audit;

/**
 * Consumes audit events asynchronously after the associated business transaction completes. The starter supplies a
 * JDBC-backed consumer when the JDBC module is present. Registering an application bean of this type replaces that
 * default, allowing the application to choose another destination.
 *
 * <p>For example, an application can publish events to its MQ client with a bean like this:
 *
 * <pre>{@code
 * @Bean
 * AuditEventConsumer auditEventConsumer(MqTemplate mq) {
 *     return event -> mq.convertAndSend("audit-events", event);
 * }
 * }</pre>
 *
 * <p>The consumer is invoked by a bounded executor and can be called concurrently. Implementations should be
 * thread-safe. Exceptions are logged by the starter; failed events are not retried automatically. Replacing the
 * executor with one that runs commands inline also makes this method run on the transaction-completion thread.
 */
@FunctionalInterface
public interface AuditEventConsumer {
    void consume(AuditEvent event);
}

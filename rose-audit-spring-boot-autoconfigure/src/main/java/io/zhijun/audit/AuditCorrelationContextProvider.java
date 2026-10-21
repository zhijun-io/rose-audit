package io.zhijun.audit;

/**
 * Supplies request and trace identifiers from the host application's context without choosing a tracing library.
 */
@FunctionalInterface
public interface AuditCorrelationContextProvider {
    AuditCorrelationContext currentContext();
}

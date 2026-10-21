package io.zhijun.audit;

/** Supplies the tenant identifier from the application's tenant context without implementing tenant resolution. */
@FunctionalInterface
public interface AuditTenantProvider {
    /** Return the current tenant ID, or {@code null} when the event is not tenant-scoped. */
    String getTenantId();
}

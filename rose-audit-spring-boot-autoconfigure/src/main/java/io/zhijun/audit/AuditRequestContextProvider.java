package io.zhijun.audit;

/** Supplies request metadata such as client IP and user agent; return an empty context outside a request. */
@FunctionalInterface
public interface AuditRequestContextProvider {
    AuditRequestContext currentContext();
}

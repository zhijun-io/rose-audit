package io.zhijun.audit;

/** HTTP metadata captured before an audit event is published; fields are empty outside an HTTP request. */
public record AuditRequestContext(
        String httpMethod, String url, String queryString, Integer httpStatus, String clientIp, String userAgent) {
    public static AuditRequestContext empty() {
        return new AuditRequestContext("", "", "", null, "", "");
    }
}

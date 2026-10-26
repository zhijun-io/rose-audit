package io.zhijun.audit.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rose.audit.http")
public class AuditHttpProperties {
    private List<String> include = List.of();
    private List<String> queryParametersAllowlist = List.of();
    private List<String> exclude = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            "/error",
            "/health",
            "/health/**",
            "/static/**",
            "/assets/**",
            "/**/*.css",
            "/**/*.js",
            "/**/*.map",
            "/**/*.png",
            "/**/*.jpg",
            "/**/*.jpeg",
            "/**/*.gif",
            "/**/*.svg",
            "/**/*.ico",
            "/**/*.woff",
            "/**/*.woff2");

    public List<String> getInclude() {
        return include;
    }

    public void setInclude(List<String> include) {
        this.include = include == null ? List.of() : List.copyOf(include);
    }

    public List<String> getQueryParametersAllowlist() {
        return queryParametersAllowlist;
    }

    public void setQueryParametersAllowlist(List<String> queryParametersAllowlist) {
        this.queryParametersAllowlist =
                queryParametersAllowlist == null ? List.of() : List.copyOf(queryParametersAllowlist);
    }

    public List<String> getExclude() {
        return exclude;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}

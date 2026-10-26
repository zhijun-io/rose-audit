package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditHttpPropertiesTest {
    @Test
    void exposesEmptyIncludesAndQueryAllowlistWithDefaultExclusions() {
        var properties = new AuditHttpProperties();

        assertThat(properties.getInclude()).isEmpty();
        assertThat(properties.getQueryParametersAllowlist()).isEmpty();
        assertThat(properties.getExclude()).contains("/actuator/**", "/error", "/**/*.js", "/**/*.css");
    }

    @Test
    void copiesConfiguredListsAndReplacesNullListsWithEmptyLists() {
        var include = new ArrayList<>(List.of("/auth/**"));
        var allowlist = new ArrayList<>(List.of("page", "sort"));
        var exclude = new ArrayList<>(List.of("/internal/**"));
        var properties = new AuditHttpProperties();

        properties.setInclude(include);
        properties.setQueryParametersAllowlist(allowlist);
        properties.setExclude(exclude);
        include.add("/later/**");
        allowlist.clear();
        exclude.clear();

        assertThat(properties.getInclude()).containsExactly("/auth/**");
        assertThat(properties.getQueryParametersAllowlist()).containsExactly("page", "sort");
        assertThat(properties.getExclude()).containsExactly("/internal/**");
        assertThatThrownBy(() -> properties.getInclude().add("/mutate/**"))
                .isInstanceOf(UnsupportedOperationException.class);

        properties.setInclude(null);
        properties.setQueryParametersAllowlist(null);
        properties.setExclude(null);
        assertThat(properties.getInclude()).isEmpty();
        assertThat(properties.getQueryParametersAllowlist()).isEmpty();
        assertThat(properties.getExclude()).isEmpty();
    }
}

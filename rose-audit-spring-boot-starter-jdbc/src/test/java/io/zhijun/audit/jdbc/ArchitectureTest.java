package io.zhijun.audit.jdbc;

import com.enofex.taikai.Taikai;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void taikaiRulesAreSatisfied() {
        Taikai.builder()
                .namespace("io.zhijun.audit.jdbc")
                .java(java -> java.noUsageOfDeprecatedAPIs()
                        .noUsageOfSystemOutOrErr()
                        .imports(imports -> imports.shouldHaveNoCycles()))
                .spring(spring -> spring.noAutowiredFields())
                .build()
                .checkAll();
    }
}

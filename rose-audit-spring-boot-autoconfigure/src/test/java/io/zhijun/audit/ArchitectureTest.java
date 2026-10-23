package io.zhijun.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.enofex.taikai.Taikai;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "io.zhijun.audit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule auditApiDoesNotDependOnImplementations = noClasses()
            .that()
            .resideInAPackage("io.zhijun.audit")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..autoconfigure..", "..web..", "..jdbc..")
            .because("the public audit API must stay independent of its implementations");

    @Test
    void taikaiRulesAreSatisfied() {
        Taikai.builder()
                .namespace("io.zhijun.audit")
                .java(java -> java.noUsageOfDeprecatedAPIs()
                        .noUsageOfSystemOutOrErr()
                        .imports(imports -> imports.shouldHaveNoCycles()))
                .spring(spring -> spring.noAutowiredFields())
                .build()
                .checkAll();
    }
}

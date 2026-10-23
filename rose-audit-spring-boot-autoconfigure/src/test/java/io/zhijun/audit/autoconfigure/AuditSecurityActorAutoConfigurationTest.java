package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** Verifies Security principal selection and that an application provider replaces it. */
class AuditSecurityActorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(AuditSecurityActorAutoConfiguration.class, AuditAutoConfiguration.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesTheAuthenticatedPrincipalName() {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "user-1", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));

        contextRunner.run(context -> {
            AuditActorProvider actor = context.getBean(AuditActorProvider.class);
            assertThat(actor.currentActorId()).isNull();
            assertThat(actor.currentActorName()).isEqualTo("user-1");
        });
    }

    @Test
    void leavesAnonymousAndMissingSessionsEmpty() {
        contextRunner.run(context -> {
            AuditActorProvider actor = context.getBean(AuditActorProvider.class);
            assertThat(actor.currentActorId()).isNull();
            assertThat(actor.currentActorName()).isNull();
        });

        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        contextRunner.run(context -> {
            AuditActorProvider actor = context.getBean(AuditActorProvider.class);
            assertThat(actor.currentActorId()).isNull();
            assertThat(actor.currentActorName()).isNull();
        });
    }

    @Test
    void keepsAnApplicationProvider() {
        contextRunner.withBean(AuditActorProvider.class, () -> () -> "app-user").run(context -> {
            assertThat(context).hasSingleBean(AuditActorProvider.class);
            assertThat(context.getBean(AuditActorProvider.class).currentActorId())
                    .isEqualTo("app-user");
        });
    }

    @Test
    void doesNotCreateASecurityActorWhenAuditIsDisabled() {
        contextRunner
                .withPropertyValues("rose.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuditActorProvider.class));
    }
}

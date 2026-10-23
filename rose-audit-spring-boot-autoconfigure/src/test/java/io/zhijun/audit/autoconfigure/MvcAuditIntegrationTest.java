package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verifies controller auditing through DispatcherServlet, including the error-page dispatch. */
@SpringBootTest(classes = MvcAuditIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class MvcAuditIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingAuditEvents events;

    @Test
    void recordsTheMappedControllerCallWithoutTheQueryStringOrResponseStatus() throws Exception {
        events.clear();

        mockMvc.perform(get("/ok").queryParam("token", "secret")).andExpect(status().isOk());

        assertThat(events.recorded()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("SampleApi.ok");
            assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
            assertThat(event.actorId()).isEqualTo("mvc-user");
            assertThat(event.queryString()).isNull();
            assertThat(event.httpStatus()).isNull();
        });
    }

    @Test
    void recordsAControllerFailureWithoutAnErrorControllerSuccess() throws Exception {
        events.clear();

        assertThatThrownBy(() -> mockMvc.perform(get("/fail"))).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(events.recorded()).extracting(AuditEvent::action).containsExactly("SampleApi.fail");
        assertThat(events.recorded()).extracting(AuditEvent::outcome).containsExactly(AuditEvent.Outcome.FAILURE);

        events.clear();
        mockMvc.perform(get("/error"));
        assertThat(events.recorded()).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SampleApi.class)
    static class TestApplication {
        @Bean
        AuditActorProvider auditActorProvider() {
            return () -> "mvc-user";
        }

        @Bean
        RecordingAuditEvents recordingAuditEvents() {
            return new RecordingAuditEvents();
        }
    }

    static class RecordingAuditEvents {
        private final List<AuditEvent> events = new ArrayList<>();

        @EventListener
        void onEvent(AuditEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<AuditEvent> recorded() {
            return List.copyOf(events);
        }
    }

    @RestController
    static class SampleApi {
        @GetMapping("/ok")
        public String ok() {
            return "ok";
        }

        @GetMapping("/fail")
        public String fail() {
            throw new IllegalStateException("failed");
        }

        public String notMapped() {
            return "ignored";
        }
    }
}

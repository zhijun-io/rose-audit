package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.zhijun.audit.AuditEvent;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@SpringBootTest(
        classes = EmbeddedServletAuditIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"rose.audit.http.include[0]=/security/**", "rose.audit.http.include[1]=/logout"})
class EmbeddedServletAuditIntegrationTest {
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RecordingEvents events;

    @Autowired
    private ErrorDispatchTracker errorDispatchTracker;

    @BeforeEach
    void clearEvents() {
        events.clear();
        errorDispatchTracker.clear();
    }

    @Test
    void recordsSendErrorOnceWithContainerFinalStatus() {
        HttpResponse<String> response = get("/send-error");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT.value());
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.action()).isEqualTo("GET /send-error");
                            assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
                            assertThat(event.httpStatus()).isEqualTo(418);
                        }));
    }

    @Test
    void recordsContainerErrorDispatchOnceForControllerException() {
        HttpResponse<String> response = get("/throws");

        assertThat(response.statusCode()).isBetween(500, 599);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(errorDispatchTracker.errorPageVisits()).isEqualTo(1);
            assertThat(errorDispatchTracker.forwardedPageVisits()).isEqualTo(1);
        });
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.action()).isEqualTo("GET /throws");
                            assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
                        }));
    }

    @Test
    void recordsRequestShortCircuitedBeforeMvcWhenExplicitlyIncluded() {
        String credentials = java.util.Base64.getEncoder()
                .encodeToString("invalid-user:invalid-password".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = get("/security/denied", "Basic " + credentials);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(events.recorded()).anySatisfy(event -> {
                assertThat(event.type()).isEqualTo(AuditEvent.Type.HTTP_REQUEST);
                assertThat(event.action()).isEqualTo("GET /security/**");
                assertThat(event.httpStatus()).isEqualTo(401);
                assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
            });
            assertThat(events.recorded()).anySatisfy(event -> {
                assertThat(event.type()).isEqualTo(AuditEvent.Type.AUTHENTICATION);
                assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
                assertThat(event.actorName()).isNull();
                assertThat(event.errorMessage()).isNull();
            });
        });
    }

    @Test
    void recordsBasicAuthenticationSuccessSeparatelyFromTheHttpRequest() {
        String credentials = java.util.Base64.getEncoder()
                .encodeToString("test-user:test-password".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = get("/security/ok", "Basic " + credentials);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(events.recorded()).anySatisfy(event -> {
                assertThat(event.type()).isEqualTo(AuditEvent.Type.HTTP_REQUEST);
                assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
            });
            assertThat(events.recorded()).anySatisfy(event -> {
                assertThat(event.type()).isEqualTo(AuditEvent.Type.AUTHENTICATION);
                assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
                assertThat(event.actorName()).isEqualTo("test-user");
            });
        });
    }

    @Test
    void recordsJwtProviderAuthenticationSuccess() {
        HttpResponse<String> response = get("/jwt/ok", "Bearer test-token");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded()).anySatisfy(event -> {
                    assertThat(event.type()).isEqualTo(AuditEvent.Type.AUTHENTICATION);
                    assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
                    assertThat(event.actorName()).isEqualTo("jwt-user");
                }));
    }

    @Test
    void keepsTheAuthenticatedActorAfterLogoutClearsTheSecurityContext() {
        String credentials = java.util.Base64.getEncoder()
                .encodeToString("test-user:test-password".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> login = get("/security/ok", "Basic " + credentials);
        String session = login.headers().allValues("set-cookie").stream()
                .filter(value -> value.regionMatches(true, 0, "JSESSIONID=", 0, "JSESSIONID=".length()))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("basic authentication did not create a session"));
        events.clear();

        HttpResponse<String> logout = post("/logout", session);

        assertThat(logout.statusCode()).isBetween(200, 399);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded()).anySatisfy(event -> {
                    assertThat(event.type()).isEqualTo(AuditEvent.Type.HTTP_REQUEST);
                    assertThat(event.action()).isEqualTo("POST /logout");
                    assertThat(event.actorName()).isEqualTo("test-user");
                }));
    }

    @Test
    void recordsAsyncRequestOnlyAfterServletAsyncCompletion() {
        HttpResponse<String> response = get("/async");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.action()).isEqualTo("GET /async");
                            assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
                        }));
    }

    @Test
    void recordsAsyncTimeoutAsFailureOnce() {
        HttpResponse<String> response = get("/async-timeout");

        assertThat(response.statusCode()).isBetween(400, 599);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(events.recorded())
                        .singleElement()
                        .satisfies(event -> {
                            assertThat(event.action()).isEqualTo("GET /async-timeout");
                            assertThat(event.outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
                            assertThat(event.errorType())
                                    .isEqualTo(java.util.concurrent.TimeoutException.class.getName());
                        }));
    }

    private HttpResponse<String> get(String path) {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String authorization) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (authorization != null) request.header("Authorization", authorization);
            return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception failure) {
            throw new IllegalStateException("Embedded server request failed", failure);
        }
    }

    private HttpResponse<String> post(String path, String cookie) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Cookie", cookie)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception failure) {
            throw new IllegalStateException("Embedded server request failed", failure);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Api.class)
    static class TestApplication {
        @Bean
        RecordingEvents recordingEvents() {
            return new RecordingEvents();
        }

        @Bean
        ErrorDispatchTracker errorDispatchTracker() {
            return new ErrorDispatchTracker();
        }

        @Bean
        ErrorPageRegistrar errorPageRegistrar() {
            return registry ->
                    registry.addErrorPages(new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/container-error"));
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            var sessions = new HttpSessionSecurityContextRepository();
            return http.securityContext(context -> context.securityContextRepository(sessions))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/security/**", "/jwt/**")
                            .authenticated()
                            .anyRequest()
                            .permitAll())
                    .csrf(csrf -> csrf.disable())
                    .httpBasic(basic -> basic.securityContextRepository(sessions))
                    .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                    .build();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "jwt-user")
                    .build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(User.withUsername("test-user")
                    .password("{noop}test-password")
                    .roles("USER")
                    .build());
        }
    }

    @RestController
    static class Api {
        private final ErrorDispatchTracker errorDispatchTracker;

        Api(ErrorDispatchTracker errorDispatchTracker) {
            this.errorDispatchTracker = errorDispatchTracker;
        }

        @GetMapping("/send-error")
        void sendError(HttpServletResponse response) throws java.io.IOException {
            response.sendError(HttpStatus.I_AM_A_TEAPOT.value());
        }

        @GetMapping("/throws")
        String throwsException() {
            throw new IllegalStateException("container-error-dispatch");
        }

        @GetMapping("/container-error")
        void containerError(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, java.io.IOException {
            errorDispatchTracker.errorPageVisited();
            request.getRequestDispatcher("/forwarded-error").forward(request, response);
        }

        @GetMapping("/forwarded-error")
        String forwardedError() {
            errorDispatchTracker.forwardedPageVisited();
            return "handled-error";
        }

        @GetMapping("/async")
        Callable<String> async() {
            return () -> "async";
        }

        @GetMapping("/async-timeout")
        DeferredResult<String> asyncTimeout() {
            return new DeferredResult<>(100L);
        }

        @GetMapping("/security/ok")
        String secured() {
            return "secured";
        }

        @GetMapping("/jwt/ok")
        String jwtSecured() {
            return "jwt-secured";
        }
    }

    static class ErrorDispatchTracker {
        private final AtomicInteger errorPageVisits = new AtomicInteger();
        private final AtomicInteger forwardedPageVisits = new AtomicInteger();

        void errorPageVisited() {
            errorPageVisits.incrementAndGet();
        }

        void forwardedPageVisited() {
            forwardedPageVisits.incrementAndGet();
        }

        int errorPageVisits() {
            return errorPageVisits.get();
        }

        int forwardedPageVisits() {
            return forwardedPageVisits.get();
        }

        void clear() {
            errorPageVisits.set(0);
            forwardedPageVisits.set(0);
        }
    }

    static class RecordingEvents {
        private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void record(AuditEvent event) {
            events.add(event);
        }

        List<AuditEvent> recorded() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}

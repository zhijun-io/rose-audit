package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditEvent;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuditHttpStateTest {
    @Test
    void claimKeepsTheFirstFailureAndIgnoresAFailureThatArrivesAfterCompletion() {
        var state = new AuditHttpState();
        state.include("GET /orders/{id}", snapshot(state.requestId()));
        var timeout = new java.util.concurrent.TimeoutException("timeout");

        state.fail(timeout);
        state.fail(new IllegalStateException("later error"));
        AuditHttpState.Decision decision = state.claimCompletion();
        state.fail(new IllegalStateException("late error"));

        assertThat(decision.failure()).isSameAs(timeout);
        assertThat(decision.snapshot().requestId()).isEqualTo(state.requestId());
        assertThat(state.claimCompletion()).isNull();
        assertThat(state.snapshot()).isNull();
    }

    @Test
    void keepsLogoutActorUntilALaterSnapshotSuppliesOne() {
        var state = new AuditHttpState();
        state.include("POST /logout", snapshot(state.requestId()));

        state.fillActorIfAbsent(null, "alice");
        state.fillActorIfAbsent(null, "bob");

        assertThat(state.snapshot().actorName()).isEqualTo("alice");
        assertThat(state.snapshot().actorId()).isNull();
    }

    @Test
    void concurrentClaimsCompleteTheRequestOnlyOnce() throws Exception {
        var state = new AuditHttpState();
        state.include("GET /orders/{id}", snapshot(state.requestId()));
        var pool = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(8);
        var winners = new AtomicInteger();
        try {
            for (int i = 0; i < 8; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (state.claimCompletion() != null) {
                            winners.incrementAndGet();
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners.get()).isEqualTo(1);
    }

    private static AuditEvent snapshot(String requestId) {
        return new AuditEvent(
                "event-1",
                AuditEvent.Type.HTTP_REQUEST,
                Instant.EPOCH,
                null,
                requestId,
                null,
                null,
                null,
                "http.request",
                AuditEvent.Outcome.UNKNOWN,
                null,
                null,
                null,
                "GET",
                "/orders/{id}",
                null,
                null,
                null,
                null);
    }
}

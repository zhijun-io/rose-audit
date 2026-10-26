package io.zhijun.audit.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.jdbc.AuditEventQueryRequest;
import io.zhijun.audit.jdbc.AuditEventRecord;
import io.zhijun.audit.jdbc.AuditEventService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DemoControllerTest {
    private final DemoOperationsService operations = mock(DemoOperationsService.class);
    private final AuditEventService auditEvents = mock(AuditEventService.class);
    private final DemoController controller = new DemoController(operations, auditEvents);

    @Test
    void delegatesSuccessfulOperationsAndWrapsTheResponse() {
        var request = new DemoOperationsService.DemoOperationRequest("device-17");
        when(operations.succeed(request)).thenReturn("Operation completed for device-17");

        assertThat(controller.succeed(request).message()).isEqualTo("Operation completed for device-17");
    }

    @Test
    void delegatesFailedOperations() {
        var request = new DemoOperationsService.DemoOperationRequest("device-17");
        when(operations.fail(request)).thenReturn("Operation failed");

        assertThat(controller.fail(request).message()).isEqualTo("Operation failed");
    }

    @Test
    void searchesByActorAndOutcomeUsingTheSuppliedPageable() {
        var pageable = PageRequest.of(1, 10);
        var page = new PageImpl<AuditEventRecord>(List.of(), pageable, 0);
        when(auditEvents.search(
                        new AuditEventQueryRequest("actor-1", null, AuditEvent.Outcome.FAILURE, null, null), pageable))
                .thenReturn(page);

        assertThat(controller.search("actor-1", AuditEvent.Outcome.FAILURE, pageable))
                .isSameAs(page);
        verify(auditEvents)
                .search(new AuditEventQueryRequest("actor-1", null, AuditEvent.Outcome.FAILURE, null, null), pageable);
    }

    @Test
    void mapsOperationFailuresToTheSampleResponseMessage() {
        assertThat(controller.handleOperationFailure(new IllegalStateException("invalid operation")))
                .isEqualTo(new DemoController.DemoMessage("invalid operation"));
    }
}

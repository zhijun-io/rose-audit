package io.zhijun.audit.sample;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.jdbc.AuditEventQueryRequest;
import io.zhijun.audit.jdbc.AuditEventRecord;
import io.zhijun.audit.jdbc.AuditEventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes sample audited operations and a pageable endpoint for browsing persisted audit events. */
@RestController
@RequestMapping("/api")
public class DemoController {
    private final DemoOperationsService operations;
    private final AuditEventService auditEvents;

    public DemoController(DemoOperationsService operations, AuditEventService auditEvents) {
        this.operations = operations;
        this.auditEvents = auditEvents;
    }

    @PostMapping("/demo/success")
    public DemoMessage succeed(@RequestBody DemoOperationsService.DemoOperationRequest request) {
        return new DemoMessage(operations.succeed(request));
    }

    @PostMapping("/demo/failure")
    public DemoMessage fail(@RequestBody DemoOperationsService.DemoOperationRequest request) {
        return new DemoMessage(operations.fail(request));
    }

    @GetMapping("/audit-events")
    public Page<AuditEventRecord> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditEvent.Outcome outcome,
            Pageable pageable) {
        var filter = new AuditEventQueryRequest(actorId, null, outcome, null, null);
        return auditEvents.search(filter, pageable);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DemoMessage handleOperationFailure(IllegalStateException exception) {
        return new DemoMessage(exception.getMessage());
    }

    /** Simple response body used by the demonstration operation endpoints. */
    public record DemoMessage(String message) {}
}

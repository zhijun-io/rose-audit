package io.zhijun.audit.sample;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides transactional operations that are audited at their Controller request boundary. */
@Service
@Transactional
public class DemoOperationsService {
    public String succeed(DemoOperationRequest request) {
        return "Operation completed for " + request.targetId();
    }

    public String fail(DemoOperationRequest request) {
        throw new IllegalStateException("Sample operation failed intentionally");
    }

    /** Request payload identifying the demonstration record affected by an operation. */
    public record DemoOperationRequest(String targetId) {}
}

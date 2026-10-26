package io.zhijun.audit.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DemoOperationsServiceTest {
    private final DemoOperationsService service = new DemoOperationsService();

    @Test
    void completesSuccessfulOperationForTheRequestedTarget() {
        assertThat(service.succeed(new DemoOperationsService.DemoOperationRequest("device-17")))
                .isEqualTo("Operation completed for device-17");
    }

    @Test
    void reportsTheIntentionalFailure() {
        assertThatThrownBy(() -> service.fail(new DemoOperationsService.DemoOperationRequest("device-17")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sample operation failed intentionally");
    }
}

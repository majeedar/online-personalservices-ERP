package edu.university.ops.shared.integration;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Synchronous external calls made inside a user request (e.g. cost-centre
 * validation): retried, recorded as an integration run, and on final failure
 * recorded as an integration error before the exception reaches the user.
 */
@Component
public class IntegrationCalls {

    private final IntegrationMonitor monitor;
    private final Retry retry;

    IntegrationCalls(IntegrationMonitor monitor, Retry retry) {
        this.monitor = monitor;
        this.retry = retry;
    }

    public <T> T call(String interfaceName, String externalReference, Supplier<T> action) {
        UUID runId = monitor.startRun(interfaceName, IntegrationRun.Trigger.EVENT);
        try {
            T result = retry.call(interfaceName, action);
            monitor.finishRun(runId, 1, 1, 0);
            return result;
        } catch (ExternalSystemException e) {
            monitor.recordError(runId, externalReference, e.errorCode(), e.getMessage(), 0, null);
            monitor.finishRun(runId, 1, 0, 1);
            throw e;
        }
    }
}

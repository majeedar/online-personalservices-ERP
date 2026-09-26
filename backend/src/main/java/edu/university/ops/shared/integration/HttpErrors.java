package edu.university.ops.shared.integration;

import java.util.function.Supplier;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Maps technical HTTP failures to {@link ExternalSystemException}. Client errors
 * (4xx) propagate unchanged: adapters decide what a 404 or 422 means.
 */
public final class HttpErrors {

    private HttpErrors() {
    }

    public static <T> T call(ExternalSystem system, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new ExternalSystemException(system, "CONNECTION_FAILED",
                    system + " is not reachable (" + e.getMostSpecificCause().getClass().getSimpleName() + ")", e);
        } catch (HttpServerErrorException e) {
            throw new ExternalSystemException(system, "HTTP_" + e.getStatusCode().value(),
                    system + " answered with HTTP " + e.getStatusCode().value(), e);
        }
    }
}

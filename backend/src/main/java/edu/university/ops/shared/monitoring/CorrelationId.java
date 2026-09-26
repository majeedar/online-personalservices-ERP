package edu.university.ops.shared.monitoring;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Access to the correlation ID of the current unit of work (AGENT.md §53).
 * It is kept in the SLF4J MDC, so structured logs carry it automatically.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Accept caller-supplied IDs only if they are harmless in logs and headers. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String sanitizeOrGenerate(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }

    /** For batch jobs and event listeners that start a unit of work outside HTTP. */
    public static String startNew() {
        String id = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        return id;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}

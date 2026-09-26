package edu.university.ops.shared.integration;

/**
 * Delivers one type of outbox event to an external system. Implemented by the
 * business module that owns the export (e.g. travel), so {@code shared} stays
 * independent of modules. Handlers must be idempotent: the same event may be
 * delivered more than once and must use {@link OutboxEvent#getIdempotencyKey()}.
 */
public interface OutboxHandler {

    String eventType();

    /** Interface name recorded in integration runs, e.g. "FINANCE_POSTING". */
    String interfaceName();

    /**
     * @throws ExternalSystemException for technical failures (the event is retried)
     */
    void handle(OutboxEvent event);
}

package edu.university.ops.shared.retention;

import java.time.LocalDate;

/**
 * One module's share of data retention. Implemented by the module that owns the
 * data; the shared {@code data-retention} batch job runs all tasks. Tasks must be
 * idempotent: records already anonymised are never processed again.
 */
public interface RetentionTask {

    /** Short name for the batch history, e.g. "absence-requests". */
    String name();

    /** Applies retention as of {@code today}; returns the number of records anonymised or deleted. */
    int apply(LocalDate today, RetentionProperties properties);
}

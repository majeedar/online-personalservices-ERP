package edu.university.ops.shared.batch;

import edu.university.ops.shared.i18n.Text;
import java.time.Instant;
import java.util.Optional;

/**
 * A batch job (AGENT.md §27–28). Implemented by the module that owns the data;
 * the shared batch infrastructure schedules it, records its history and prevents
 * concurrent runs. Jobs must be idempotent: running twice never duplicates data.
 */
public interface BatchJob {

    /** Stable name, used in URLs and configuration, e.g. "employee-sync". */
    String name();

    /** Shown in the batch overview, in the reader's language (ADR-020). */
    Text description();

    /** Default cron expression; overridable via {@code ops.batch.schedules.<name>}. Empty = manual only. */
    default String defaultCron() {
        return "";
    }

    void run(Context context);

    /** Execution context handed to a job. */
    interface Context {

        /** Start of the last successful run, for incremental processing ("changed since"). */
        Optional<Instant> lastSuccessfulRun();

        void success();

        void failure(String recordReference, String errorCode, String message);

        /** Records {@code count} successes at once. */
        default void successes(int count) {
            for (int i = 0; i < count; i++) {
                success();
            }
        }
    }
}

package edu.university.ops.shared.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised platform settings (AGENT.md §48). Environment-specific values
 * come from application.yml / environment variables, never from code.
 *
 * @param timezone university timezone; the business date of a timestamp is derived in it (ADR-010)
 * @param holidayRegion region code used to select the public-holiday calendar
 */
@Validated
@ConfigurationProperties(prefix = "ops")
public record OpsProperties(@NotNull ZoneId timezone,
                            @NotNull String holidayRegion,
                            @Valid @DefaultValue Workflow workflow,
                            @Valid @DefaultValue Mail mail,
                            @Valid @DefaultValue Time time) {

    /** @param statutoryBreaks deduct missing statutory minimum breaks (30 min after 6 h, 45 min after 9 h) */
    public record Time(@DefaultValue("true") boolean statutoryBreaks) {
    }

    /**
     * @param taskDueDays       days until a new approval task is due
     * @param reminderAfterDays open tasks older than this get a reminder (batch job)
     */
    public record Workflow(@DefaultValue("5") @Min(1) int taskDueDays,
                           @DefaultValue("3") @Min(1) int reminderAfterDays) {
    }

    /**
     * @param enabled send notification mails (MailHog in the prototype); in-app notifications always work
     * @param from    sender address
     */
    public record Mail(@DefaultValue("false") boolean enabled,
                       @DefaultValue("no-reply@uni.example") String from) {
    }
}

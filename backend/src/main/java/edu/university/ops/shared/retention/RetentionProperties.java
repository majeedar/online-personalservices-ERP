package edu.university.ops.shared.retention;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retention periods in days after a record is finished (AGENT.md §82). The
 * defaults are placeholders; each university sets them from its records policy.
 *
 * @param absenceDays        after the end of a finished absence request
 * @param travelDays         after the end of a finished trip (accounting records: 10 years)
 * @param timeCorrectionDays after the day of a decided time correction
 * @param notificationDays   after a notification was created
 */
@Validated
@ConfigurationProperties(prefix = "ops.retention")
public record RetentionProperties(@DefaultValue("1095") @Min(1) int absenceDays,
                                  @DefaultValue("3650") @Min(1) int travelDays,
                                  @DefaultValue("730") @Min(1) int timeCorrectionDays,
                                  @DefaultValue("180") @Min(1) int notificationDays) {
}

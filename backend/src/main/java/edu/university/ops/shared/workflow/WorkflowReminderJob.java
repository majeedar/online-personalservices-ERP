package edu.university.ops.shared.workflow;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.batch.BatchJob;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reminds approvers of tasks open longer than {@code ops.workflow.reminder-after-days}
 * (AGENT.md §27.7). At most one reminder per task and person per day, so a re-run
 * never sends duplicates.
 */
@Component
class WorkflowReminderJob implements BatchJob {

    private final WorkflowService workflow;
    private final DelegationService delegations;
    private final NotificationService notifications;
    private final PersonDirectory persons;
    private final Clock clock;

    WorkflowReminderJob(WorkflowService workflow, DelegationService delegations, NotificationService notifications,
                        PersonDirectory persons, Clock clock) {
        this.workflow = workflow;
        this.delegations = delegations;
        this.notifications = notifications;
        this.persons = persons;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "workflow-reminder";
    }

    @Override
    public Text description() {
        return Text.of("Remind approvers of overdue approval tasks");
    }

    @Override
    public String defaultCron() {
        return "0 0 7 * * MON-FRI";
    }

    @Override
    public void run(Context context) {
        LocalDate today = LocalDate.now(clock);
        Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();
        for (var task : workflow.overdueForReminder()) {
            Set<UUID> recipients = new LinkedHashSet<>();
            if (task.assignedEmployeeId() != null) {
                recipients.add(task.assignedEmployeeId());
                recipients.addAll(delegations.effectiveDelegatesOf(task.assignedEmployeeId(), task.approvalType(),
                        today));
            } else if (task.assignedRole() != null) {
                recipients.addAll(persons.activeEmployeesWithRole(Role.valueOf(task.assignedRole())));
            }
            for (UUID recipient : recipients) {
                if (notifications.alreadyNotifiedSince(recipient, NotificationType.TASK_REMINDER, task.id(),
                        startOfDay)) {
                    continue;
                }
                notifications.notify(recipient, NotificationType.TASK_REMINDER, "UserTask", task.id(),
                        Text.of("Reminder: approval pending"), Text.of("{task} is waiting since {date}.", "task",
                                task.titleText(), "date", task.createdAt().atZone(clock.getZone()).toLocalDate()));
            }
            context.success();
        }
    }
}

package edu.university.ops.employee.application;

import edu.university.ops.employee.application.EmployeeSyncService.Outcome;
import edu.university.ops.employee.application.PersonnelEmployeeMapper.EmployeeImportData;
import edu.university.ops.employee.domain.EmployeeMasterDataGateway;
import edu.university.ops.employee.domain.EmployeeMasterDataGateway.ExternalEmployee;
import edu.university.ops.shared.batch.BatchJob;
import edu.university.ops.shared.integration.ExternalSystemException;
import edu.university.ops.shared.integration.IntegrationMonitor;
import edu.university.ops.shared.integration.IntegrationRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;

/**
 * The personnel-ERP batch jobs (AGENT.md §27.1, §27.3, §27.4). Each follows the
 * same pipeline: changed records → map → validate → upsert → audit + metrics,
 * recorded as a batch run and an integration run.
 */
@Configuration
class EmployeeSyncJobs {

    @Bean
    BatchJob employeeSyncJob(EmployeeMasterDataGateway gateway, PersonnelEmployeeMapper mapper,
                             EmployeeSyncService sync, IntegrationMonitor monitor) {
        return new SyncJob("employee-sync", "Synchronise employees and employments from the personnel ERP",
                "0 0 2 * * *", "PERSONNEL_EMPLOYEE_SYNC", gateway, mapper, monitor, sync::applyMasterData);
    }

    @Bean
    BatchJob supervisorSyncJob(EmployeeMasterDataGateway gateway, PersonnelEmployeeMapper mapper,
                               EmployeeSyncService sync, IntegrationMonitor monitor) {
        return new SyncJob("supervisor-sync", "Synchronise supervisor / approver relationships", "0 15 2 * * *",
                "PERSONNEL_SUPERVISOR_SYNC", gateway, mapper, monitor, sync::applySupervisor);
    }

    @Bean
    BatchJob workScheduleSyncJob(EmployeeMasterDataGateway gateway, PersonnelEmployeeMapper mapper,
                                 EmployeeSyncService sync, IntegrationMonitor monitor) {
        return new SyncJob("work-schedule-sync", "Refresh (part-time) work schedules from the personnel ERP",
                "0 30 2 * * *", "PERSONNEL_WORK_SCHEDULE_SYNC", gateway, mapper, monitor, sync::applyWorkSchedule);
    }

    private record SyncJob(String name, String description, String defaultCron, String interfaceName,
                           EmployeeMasterDataGateway gateway, PersonnelEmployeeMapper mapper,
                           IntegrationMonitor monitor, Function<EmployeeImportData, Outcome> apply)
            implements BatchJob {

        @Override
        public void run(Context context) {
            UUID runId = monitor.startRun(interfaceName, IntegrationRun.Trigger.SCHEDULED);
            int read = 0;
            int written = 0;
            int failed = 0;
            try {
                List<ExternalEmployee> changed = gateway.findChangedEmployees(
                        context.lastSuccessfulRun().orElse(Instant.EPOCH));
                for (ExternalEmployee external : changed) {
                    read++;
                    var mapped = mapper.map(external);
                    if (!mapped.valid()) {
                        failed++;
                        String message = String.join("; ", mapped.problems());
                        context.failure(external.persNr(), "VALIDATION_FAILED", message);
                        monitor.recordError(runId, external.persNr(), "VALIDATION_FAILED", message, 0, null);
                        continue;
                    }
                    try {
                        if (apply.apply(mapped.data()) != Outcome.UNCHANGED) {
                            written++;
                        }
                        context.success();
                    } catch (IllegalArgumentException | DataAccessException e) {
                        failed++;
                        context.failure(external.persNr(), "APPLY_FAILED", e.getMessage());
                        monitor.recordError(runId, external.persNr(), "APPLY_FAILED", e.getMessage(), 0, null);
                    }
                }
            } catch (ExternalSystemException e) {
                failed++;
                context.failure(null, e.errorCode(), e.getMessage());
                monitor.recordError(runId, null, e.errorCode(), e.getMessage(), 0, null);
                throw e;
            } finally {
                monitor.finishRun(runId, read, written, failed);
            }
        }
    }
}

# Batch processing

Batch jobs implement `shared.batch.BatchJob` in the module that owns the data. `BatchJobService` runs them, schedules them (cron in `ops.batch.schedules`, evaluated in `ops.timezone`), records history and alerts on failure. They can be started manually from **Administration › Batch Jobs** (ERP_ADMIN) or via `POST /api/v1/admin/jobs/{name}/run`.

| Job | Default schedule | Module | What it does |
|---|---|---|---|
| `organisation-sync` | 01:30 daily | organisation | Changed units from the personnel ERP → validate → upsert by code (parents first) |
| `employee-sync` | 02:00 daily | employee | Changed employees → map → validate → upsert employee, employment, initial schedule and role; deactivate leavers |
| `supervisor-sync` | 02:15 daily | employee | `SUPERVISOR_REF` → approval relations (absence, travel, time correction); old relations are end-dated, not deleted |
| `work-schedule-sync` | 02:30 daily | employee | Changed part-time patterns → new schedule from today; the previous one is end-dated |
| `leave-entitlement-calculation` | 03:00 on 1 January | absence | Create missing yearly entitlements (pro rata, carry-over up to 10 days, expiring 31 March); recompute used/reserved from absence days. Also runs per employee on `EmployeeMasterDataChanged`. |
| `time-account-recalculation` | 01:00 daily | time | Recalculate the last 7 days of every active employee's time account |
| `workflow-reminder` | 07:00 Mon–Fri | shared/workflow | Remind approvers (and delegates / role holders) of tasks older than `reminder-after-days` |
| `integration-retry` | every 15 min | shared/integration | Deliver outbox exports whose backoff has elapsed |

Incremental jobs read "changed since" the start of their last successful run (`Context.lastSuccessfulRun()`).

## Design requirements (AGENT.md §28)

| Requirement | How |
|---|---|
| Idempotent | Upserts by business key; recalculations are deterministic; reminders at most once per task, person and day; exports carry idempotency keys |
| Restartable | Incremental jobs use the last *successful* run as watermark, so a failed run is repeated in full. At startup, runs left `RUNNING` by a crashed instance are marked `FAILED`. |
| Observable | `batch_job_run` / `batch_job_error` (shown in the admin UI); Micrometer metrics `ops.batch.runs{job,status}`, `ops.batch.records{job,result}`, `ops.batch.duration{job}`; sync jobs also write `integration_run` / `integration_error` |
| Auditable | Audit records `BATCH_MANUAL_START` and `BATCH_RUN` with the outcome, plus the changes the job made (e.g. `EMPLOYEE_IMPORTED`, `LEAVE_ENTITLEMENT_RECALCULATED`) |
| Safe against duplicate processing | Unique partial index `ux_batch_job_running`: a second concurrent start fails with `409 INVALID_WORKFLOW_STATE` |
| Alerts | Runs ending `PARTIAL` or `FAILED` notify ERP admins (`BATCH_FAILURE`, in-app and mail) |

Each record is processed in its own transaction, so one bad record never rolls back the others. The run status is `SUCCESS` (no failures), `PARTIAL` (some failed) or `FAILED` (none succeeded, or the job crashed).

## Configuration

```yaml
ops:
  batch:
    enabled: true            # false: no timers (tests), manual starts still possible
    schedules:
      employee-sync: "0 0 2 * * *"
      workflow-reminder: "-"   # "-" disables the timer for a job
```

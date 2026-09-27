# REST API

Base path `/api/v1`, JSON, session cookie + CSRF header (`X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie) for mutating requests. The live OpenAPI description is at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.

## Errors

Every error has one shape (AGENT.md §33):

```json
{ "code": "ABSENCE_OVERLAP",
  "message": "The requested absence overlaps with an existing request.",
  "correlationId": "4f0c…",
  "details": [ { "field": "startDate", "message": "must not be null" } ] }
```

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `INVALID_DATE_RANGE`, `DOCUMENT_INVALID` |
| 401 | `NOT_AUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | `NOT_AUTHORIZED`, `CSRF_TOKEN_INVALID` |
| 404 | `RESOURCE_NOT_FOUND`, `EMPLOYEE_NOT_FOUND`, `WORK_SCHEDULE_NOT_FOUND` |
| 409 | `INVALID_WORKFLOW_STATE`, `CONCURRENT_MODIFICATION`, `ABSENCE_OVERLAP`, `TIME_SEQUENCE_INVALID`, `TIME_MONTH_CLOSED` |
| 422 | `INSUFFICIENT_LEAVE_BALANCE`, `NO_LEAVE_ENTITLEMENT`, `ABSENCE_NO_WORKING_DAYS`, `LEAVE_TYPE_INACTIVE`, `EMPLOYEE_INACTIVE`, `EMPLOYMENT_NOT_COVERING`, `INVALID_REPRESENTATIVE`, `ATTACHMENT_REQUIRED`, `COST_CENTRE_INVALID`, `FUNDING_INVALID`, `TIME_MONTH_NOT_CLOSABLE` |
| 503 | `EXTERNAL_SYSTEM_UNAVAILABLE` |
| 500 | `INTERNAL_ERROR` (no stack trace; quote the correlation ID) |

## Endpoints

**Language (ADR-020):** every response text (errors, notifications, tasks, reports, job descriptions, leave-type and holiday names) follows `Accept-Language`: `de` for German, `qps-ploc` for the test language, anything else English. Field messages of `VALIDATION_FAILED` come from Bean Validation in the same language.

### Authentication and self-service
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | `{username, password}` → session |
| GET | `/auth/session` | 401 if not logged in (also issues the CSRF cookie); includes the saved `language` (`en`, `de` or null) |
| POST | `/auth/logout` | |
| GET | `/me`, `/me/employments`, `/me/work-schedule`, `/me/roles` | |
| PUT | `/me/language` | `{language: "en" \| "de"}`: interface and e-mail language (204) |
| GET | `/employees/{id}` | self, HR admin, or an active approver |
| GET | `/employees/search?q=` | staff directory: name and unit only, max 20 |
| GET | `/organisation-units`, `/holidays?year=` | |

### Absence (AGENT.md §35)
| Method | Path | Notes |
|---|---|---|
| GET | `/leave-types`, `/leave-balances?year=` | |
| GET / POST | `/absences` | list own / create draft |
| POST | `/absences/preview` | days, balance, rule violations — nothing saved |

Absence bodies: `{leaveTypeId, startDate, endDate, startDayPart?, endDayPart?, representativeId?, comment?}`. Day parts are `FULL` (default), `MORNING` or `AFTERNOON` (ADR-018). A single day uses one part; a longer absence may start `AFTERNOON` and end `MORNING`, anything else is `400 INVALID_DAY_PART`. `workingDays` and `deduction` are decimals (0.5 per half day), and every day carries its `dayPart`.

| GET / PUT | `/absences/{id}` | detail incl. days, workflow history, allowed actions / update draft |
| POST | `/absences/{id}/submit`, `/approve`, `/reject`, `/return`, `/cancel` | reject/return need `{comment}` |
| POST / GET | `/absences/{id}/documents`, `/absences/{id}/documents/{docId}` | multipart upload / download |
| GET | `/team/absences?from=&to=` | approver's team; leave type deliberately omitted |

### Travel (AGENT.md §36)
| Method | Path | Notes |
|---|---|---|
| GET | `/funding-sources` | |
| GET / POST | `/travel` | |
| GET / PUT | `/travel/{id}` | |
| POST | `/travel/{id}/submit` | validates the cost centre in the finance system |
| POST | `/travel/{id}/approve`, `/financial-approve`, `/reject`, `/return`, `/cancel` | |
| POST | `/travel/{id}/mark-completed` | |
| GET / POST | `/travel/{id}/expenses` | |
| DELETE | `/travel/{id}/expenses/{expenseId}` | |
| POST | `/travel/{id}/expenses/{expenseId}/receipt` | multipart |
| POST | `/travel/{id}/submit-expenses`, `/settle` | settle = travel office accepts the claim |

### Time (AGENT.md §37)
| Method | Path | Notes |
|---|---|---|
| GET | `/time/today` | entries, state, allowed next actions, live account |
| POST | `/time/clock-in`, `/clock-out`, `/break-start`, `/break-end` | 409 `TIME_SEQUENCE_INVALID` for invalid transitions |
| GET | `/time/month/{year}/{month}`, `/time/balance`, `/time/entries?date=` | |
| POST / GET | `/time/corrections` | `{date, operation: ADD|MODIFY|DELETE, originalEntryId, requestedTime, requestedType, reason}` |
| GET | `/time/corrections/{id}` | |
| POST | `/time/corrections/{id}/approve`, `/reject` | |
| GET | `/time/closings` | closing status of the last 12 months (TIME_ADMIN, HR_ADMIN) |
| POST | `/time/closings/{YYYY-MM}/close`, `/reopen` | reopen needs `{reason}` |

### Tasks, delegation, notifications
| Method | Path | Notes |
|---|---|---|
| GET | `/tasks`, `/tasks/{id}` | assigned, by role, or via delegation |
| POST | `/tasks/{id}/complete` | `{decision: APPROVE|REJECT|RETURN_FOR_CORRECTION|FORWARD, comment, forwardTo}` |
| GET / POST | `/delegations` | |
| POST | `/delegations/{id}/revoke` | |
| GET | `/notifications?limit=`, `/notifications/unread-count` | |
| POST | `/notifications/{id}/read`, `/notifications/read-all` | |

### Administration (AGENT.md §39) and reports (§81)
| Method | Path | Roles |
|---|---|---|
| GET | `/admin/batch-jobs`, `/admin/batch-runs`, `/admin/batch-runs/{id}/errors` | ERP_ADMIN, SUPPORT, AUDITOR |
| POST | `/admin/jobs/{jobName}/run` | ERP_ADMIN |
| GET | `/admin/integration-runs`, `/admin/integration-errors?openOnly=` | ERP_ADMIN, SUPPORT, AUDITOR |
| POST | `/admin/integration-errors/{id}/retry` | ERP_ADMIN, SUPPORT |
| POST | `/admin/integration-errors/{id}/resolve` | ERP_ADMIN |
| POST | `/admin/external-systems/{system}/outage?down=` | ERP_ADMIN (demo) |
| GET | `/admin/audit?entityType=&entityId=`, `/admin/system-health` | ERP_ADMIN, SUPPORT, AUDITOR |
| GET | `/reports`, `/reports/{id}?year=&month=&format=csv` | per report, see [security.md](security.md) |

Actuator: `/actuator/health`, `/actuator/info` (public), `/actuator/prometheus` (internal network only), others ERP_ADMIN/SUPPORT. nginx does not proxy `/actuator`.

# Integrations

Online Personalservices integrates with the university's systems of record and never lets their formats leak into business code (AGENT.md §21–26).

```text
Business service ──► Port (domain interface) ──► Adapter ──► HTTP client ──► External system
```

## Ports and adapters

| Port (owner module) | Operations | Stub adapter | HTTP adapter → mock-erp |
|---|---|---|---|
| `IdentityProvider` (shared) | authenticate, find identity | `MockIdentityProvider` | — (LDAP/OIDC adapter would go here) |
| `EmployeeMasterDataGateway` (employee) | find employee, changed employees since | `mock-data/personnel-changes.json` | `GET /mock/personnel/changes?since=`, `/employees/{nr}` |
| `OrganisationGateway` (organisation) | changed units since | `mock-data/org-unit-changes.json` | `GET /mock/personnel/organisation-units?since=` |
| `FinanceGateway` (travel) | validate cost centre, post settlement | in-memory, idempotent | `GET /mock/finance/cost-centres/{code}`, `POST /mock/finance/postings` |
| `TravelErpGateway` (travel) | export approved travel, export settlement | in-memory, idempotent | `POST /mock/travel/export`, `POST /mock/travel/settlement` |
| `NotificationGateway` (shared) | send | — | `MailNotificationAdapter` → SMTP (MailHog) |

`ops.integration.mode` selects `stub` (default: tests, local development) or `http` (Docker Compose) — ADR-015.

## Mapping (AGENT.md §24)

The ERPs use legacy field names (`PERS_NR`, `ORG_CODE`, `EMP_PERCENT`, `SUPERVISOR_REF`, `KOSTL`, `BELNR`, `REISENR`, …).

1. HTTP adapters map the wire format into the port's record (e.g. `PersonnelDto` → `ExternalEmployee`).
2. `PersonnelEmployeeMapper` validates the record and converts it into the internal model (`EmployeeImportData`). For example:
   - `EMP_PERCENT` 80 becomes an FTE of 0.8 and 32 weekly hours;
   - `WORK_DAYS` "MO,TU,WE,TH" becomes a 480-minute daily target Monday to Thursday;
   - `ORG_CODE` is resolved to the internal unit ID.

## Validation (AGENT.md §25)

Nothing invalid is written. Each record is checked for:
- personnel number format and presence;
- names and e-mail;
- user ID format (and uniqueness for new employees);
- an existing organisation unit;
- percentage in (0, 100];
- contract start present and end ≥ start;
- a known contract type;
- valid work days;
- a resolvable supervisor who is not the employee themself.

Organisation units need a known type, an existing parent, and cost-centre format `CC-nnnn`. External references returned by exports must match `AAA-nnnn…` before they are stored.

Invalid records produce a batch error and an integration error with the reason; valid records in the same run are still applied (status `PARTIAL`).

## Error handling (AGENT.md §26)

| Situation | Handling |
|---|---|
| Synchronous call in a user request (cost-centre check) | Timeouts (connect 2 s, read 5 s) and `Retry` (3 attempts, linear backoff). The call is recorded as an integration run, and a final failure is recorded as an integration error. The user gets `503 EXTERNAL_SYSTEM_UNAVAILABLE` with a correlation ID. |
| Export after a business decision | Written to `outbox_event` in the business transaction. `OutboxProcessor` delivers it with a row lock. Each failure records an integration error and backs off exponentially (10 s, 20 s, 40 s, …). After `outbox-max-attempts` (5) the event is FAILED and ERP admins get an `INTEGRATION_FAILURE` notification and mail. |
| Manual retry | Integration Monitor → Retry (`POST /admin/integration-errors/{id}/retry`). The same idempotency key is used, so the external system returns the original document. |
| Sync job | Each record is independent. Technical failure of the source aborts the run (`FAILED`) and alerts admins; validation failures make it `PARTIAL`. |

Correlation IDs are propagated to mock-erp in the `X-Correlation-Id` header (AGENT.md §53).

## Idempotency (AGENT.md §29)

- **Imports** upsert by business key (personnel number, contract ID, organisation code). A second run with the same data changes nothing and reports 0 written.
- **Exports** carry `Idempotency-Key` = `travel-export-{id}`, `travel-settlement-{id}` or `finance-posting-{id}`:
  - `outbox_event.idempotency_key` is unique, so the export is enqueued once;
  - mock-erp returns the same document number for a repeated key, so it is posted once.
- **Batch runs** are one at a time per job (unique partial index on RUNNING), and a crashed RUNNING row is released at startup.

## mock-erp

A separate Spring Boot service ([mock-erp/](../mock-erp)) with fictitious data (AGENT.md §23).
- `GET /mock/admin/status` reports each system's status.
- `POST /mock/admin/outage/{PERSONNEL_ERP|FINANCE_ERP|TRAVEL_ERP}?down=true|false` switches a system to answering `503`. The Integration Monitor has toggles for this.
- `docker compose stop mock-erp` simulates a complete outage (connection refused).

## Demo: finance outage (AGENT.md §63)

1. As `erpadmin`, open Integration Monitor and switch on *Simulate outage* for Finance ERP.
2. As `travel`, settle an expense claim. The finance posting fails, and the error appears in the monitor with its retry count.
3. Switch the outage off and click **Retry**. The posting succeeds, the error is resolved, and the trip shows the finance document number.

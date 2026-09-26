# Security and data protection

The platform processes personnel data. Security follows least privilege: every endpoint is authenticated, every object is checked, and every privileged action is audited. This document explains the principles AGENT.md §46, §47 and §82 require.

## Authentication

- **Identity** comes from the `IdentityProvider` port (AGENT.md §11). The prototype uses `MockIdentityProvider`, which checks BCrypt hashes stored in `mock-identity/identities.json` (never plain text). For unknown users it still compares against a dummy hash, so response time does not reveal which usernames exist. An LDAP, SAML or OIDC adapter can replace it without changing business code.
- **Authorisation data** comes from the `UserAccountLookup` port: the identity must belong to an **active** employee, and only roles **valid today** are granted. Roles are fixed at login, so a role change applies at the next login.
- **Sessions:** HTTP-only session cookie with `SameSite=Lax` (and `Secure` behind TLS: `SESSION_COOKIE_SECURE=true`), 30 minutes idle timeout. The session ID is rotated at login (session fixation protection).
- **CSRF:** cookie-to-header token. The token is rotated at login and logout. Login itself is CSRF-protected.
- **Same origin:** nginx serves UI and API from one origin, so there is no CORS configuration to get wrong.

## Authorisation

**URL level** (`SecurityConfiguration`): everything under `/api` requires authentication; `/api/v1/admin/**` requires ERP_ADMIN, SUPPORT or AUDITOR, and state-changing admin calls check ERP_ADMIN (or SUPPORT for retries) again.

**Object level** (AGENT.md §47), enforced in each module's application layer:

| Object | Who may read | Who may change / decide |
|---|---|---|
| Employee master data | self, HR_ADMIN, current approvers | nobody (personnel ERP is the system of record) |
| Absence request | owner, HR_ADMIN, current ABSENCE approvers, workflow participants and active delegates | owner (draft, submit, cancel); assigned approver or delegate (decide); HR_ADMIN (administrative cancellation) |
| Team calendar | approvers, for their own team; **leave type hidden** (it can be health data) | — |
| Travel request | owner, TRAVEL / FINANCIAL approvers, TRAVEL_OFFICE, workflow participants | owner; assigned approver per step; TRAVEL_OFFICE (review) |
| Time data | owner; approvers and TIME_ADMIN for corrections | owner (bookings, requests); supervisor or TIME_ADMIN (decide) |
| Tasks | assignee, role holders, active delegates | same; **never the requester** |
| Reports | per report, see below | — |
| Audit | ERP_ADMIN, SUPPORT, AUDITOR (read-only) | nobody — append-only in the database |

The rules the spec calls out explicitly (§47):
- ERP_ADMIN has technical rights but **no** access to personnel data by default.
- AUDITOR is read-only.
- A financial approver can decide only at the financial step of assigned cases.
- A supervisor cannot approve their own request.

The integration tests check all of these.

**Reports** (least privilege):

| Report | Roles |
|---|---|
| Leave usage by unit (aggregated, no individuals) | HR_ADMIN, AUDITOR |
| Monthly working time (per person) | HR_ADMIN, TIME_ADMIN |
| Pending approvals | HR_ADMIN, ERP_ADMIN, SUPPORT |
| Travel by status, estimated vs actual | TRAVEL_OFFICE, FINANCIAL_APPROVER, AUDITOR |
| Failed integrations, failed batch jobs | ERP_ADMIN, SUPPORT, AUDITOR |

CSV exports neutralise spreadsheet formula injection (cells starting with `=`, `+`, `-`, `@`).

## Input handling

- All rules are enforced on the server; the frontend only mirrors them for usability (AGENT.md §74.2).
- Bean validation on request bodies, domain validation in the application layer, and database `CHECK` and unique constraints as the last line.
- **Documents:** PDF, PNG and JPEG only, max 10 MB. They are stored under a random UUID, and original file names are sanitised metadata. Storage references are validated to prevent path traversal. Access always goes through the business object's access check.
- **Correlation IDs** from clients are accepted only if they match `[A-Za-z0-9._-]{1,64}`, which prevents log and header injection.
- **Error responses** never contain stack traces or internal messages.

## Audit (AGENT.md §20)

Audited actions:
- logins, including failed attempts (username only);
- request creation, submission, decisions, returns, cancellations, including administrative ones;
- time corrections, with before/after entries;
- delegations;
- document uploads;
- employee and organisation imports and updates;
- entitlement recalculations;
- batch runs and manual job starts;
- integration retries and resolutions;
- simulated outages.

Each record carries actor, timestamp and correlation ID. The table is append-only via a database trigger (ADR-008). Audit values are kept minimal: for personnel updates only the names of the changed fields are stored.

## Data protection (AGENT.md §82)

- **Expose only what is necessary.** The staff directory search returns only name and unit. The team calendar omits the leave type. Leave-usage reports are aggregated per unit.
- **Least privilege,** as above; technical administrators do not see personnel data.
- **Audit privileged actions,** as above.
- **Logging:** passwords, tokens, document contents, mail bodies and free-text reasons are not logged. Notification delivery failures log the ID and error class only.
- **No copies without purpose.** The platform keeps only the master data it needs (name, e-mail, unit, contracts, schedules, approvers). The personnel ERP stays the system of record, and leavers are deactivated rather than kept active.
- **Retention:** entities carry creation and decision timestamps, so a retention job can delete or anonymise closed requests after a configurable period. That job is not part of the prototype.
- **Secrets:** no secret is committed. Database, Grafana and mail settings come from environment variables (`.env.example`). The demo passwords are fictitious and documented as such.

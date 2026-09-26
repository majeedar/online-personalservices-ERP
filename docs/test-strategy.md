# Test strategy

| Level | Tool | Scope | Command |
|---|---|---|---|
| Architecture | Spring Modulith, ArchUnit | module boundaries and cycles, domain → no outer layers, api → no persistence, controllers return no entities, audit repository has no delete | `mvn test -Dtest=ArchitectureTest` |
| Unit | JUnit 5, AssertJ | pure domain rules without Spring | `mvn test` |
| Integration | Spring Boot test + MockMvc on **real PostgreSQL** (Testcontainers, or embedded when no Docker) | Flyway migrations and seed, repositories, transactions, REST APIs, security, batch jobs, integration error handling | `mvn test` |
| mock-erp | Spring Boot test | idempotent postings, outages, wire format | `cd mock-erp && mvn test` |
| Frontend unit | Vitest (Angular unit-test builder) | components and services with `HttpTestingController` | `cd frontend && npm test -- --watch=false` |
| End-to-end | Playwright (Chromium) | demo scenarios in a real browser against a running stack; fails on console errors; writes the README screenshots | `cd frontend && npx playwright test` |

## Coverage of AGENT.md §64–67

**Unit tests (§64)**
- leave-day calculation: part-time, holidays, flex and sick leave, no schedule (`AbsenceDomainTest`);
- entitlement ledger (`AbsenceDomainTest.Entitlement`);
- workflow transitions (`AbsenceDomainTest.Lifecycle`);
- time calculation: breaks, statutory break, absence credit, open shift (`TimeDomainTest`);
- time-entry sequence rules (`TimeDomainTest`);
- funding validation (`FundingRulesTest`);
- authorisation logic (integration tests below);
- mapping logic (`PersonnelEmployeeMapperTest`).

**Integration tests (§65)**
- `FoundationIntegrationTest`: migrations and seed, login and CSRF, error format and correlation IDs, audit trigger;
- `AbsenceScenarioTest`: Scenarios 1 and 2, all validation rules, return, reject, cancellation, sick leave, delegation, concurrency;
- `TimeScenarioTest`: Scenarios 5 and 6, absence credit;
- `TimeClosingTest`: monthly closing: guards, frozen days, rejected corrections, reopening, scheduler idempotency, permissions;
- `DataRetentionTest`: anonymisation after the retention period (comments, representative, attachments, decision comments), recent requests untouched, audit entry, idempotent rerun;
- `TravelScenarioTest`: Scenarios 3, 4 and 7, funding, cost centre, outage and retry, idempotency;
- `OperationsTest`: organisation, employee, supervisor and schedule sync; entitlement and time jobs; reminders; health; reports.

**Security tests (§66)**
- employee cannot read another employee's request (`AbsenceScenarioTest`, `TravelScenarioTest`, `FoundationIntegrationTest`);
- supervisor can read an assigned employee's request;
- supervisor cannot approve their own request;
- employee cannot reach admin endpoints or reports;
- auditor cannot run jobs or see working-time reports;
- financial approver cannot decide at another step or on unrelated requests;
- ERP admin gets no personnel data.

**Frontend tests (§67)**
- login (`login.spec.ts`);
- absence form validation and preview (`absence-form.spec.ts`);
- travel form validation and funding total (`travel-form.spec.ts`);
- time-action button state (`time-today.spec.ts`);
- task approval flow (`task-inbox.spec.ts`);
- month closing (`month-closing.spec.ts`);
- route guard and role-aware menu (`auth.guard.spec.ts`, `navigation.spec.ts`).

## Determinism

- Integration tests share one Spring context and one database. The clock is a `MutableClock` fixed at Monday 2026-09-21 08:00 Europe/Berlin (`TestClockConfiguration`). Tests that move it forward reset it afterwards.
- Each test uses its own date range or persona, so tests do not depend on execution order. Assertions on shared counters use deltas.
- The `test` profile disables timers (`ops.batch.enabled=false`, outbox poll interval 1 h). Tests trigger batch runs and outbox delivery explicitly.
- Integration tests use the stub adapters, so they need neither mock-erp nor network access. HTTP mode is exercised by running against mock-erp (see README) and by the Playwright suite against Docker Compose.

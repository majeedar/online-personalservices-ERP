# Demo script (10–15 minutes)

**Setup:** run `docker compose up --build` and wait until http://localhost:4200 shows the login page. Keep MailHog (http://localhost:8025) open in a second tab. Every demo account uses the password `demo123`; the login page has one-click buttons for them.

The demo data is generated relative to today, so there are always pending requests, upcoming absences, two weeks of bookings, and trips in every state.

---

## Part 1 — Employee self-service (2 min)

Log in as **`employee`** (Erika Mustermann).

1. **Dashboard:**
   - remaining leave, and the working-time balance for this month and year;
   - open requests, the next approved absence, recent travel, latest notifications.
   - *Point out:* each card is a live figure from its own module.
2. **My Profile:** personal data, work schedule (Mon–Fri 8 h) and employment history, marked *maintained in the personnel ERP*.
   - *Point out:* Online Personalservices is not the HR system of record.
3. **Working Time › Monthly overview:** daily target, worked and balance. Last week has a day marked **Missing entry**; it is used in Part 4.

## Part 2 — Absence request and approval (4 min)

1. **Absence › New request:** choose Annual leave, then a Monday–Friday period that includes a public holiday (for example the week of Ascension Day).
   - The right-hand panel calculates the working days (the holiday does not count), the current balance and the projected balance.
   - Add a representative (type "Clara") and **Save and submit**. The status is **In approval**.
2. *Optional, for the part-time rule (Scenario 2):* log in as **`parttime`** and preview Thursday → Monday. Only Thursday and Monday count, because Friday is not in her schedule.
   - *Optional, half days:* pick a single day and choose **Morning (half day)**. The preview counts 0.5 days and half the planned time.
3. Log out and log in as **`supervisor`** (Stefan Beispiel).
   - **My Tasks** shows *Approve annual leave – Erika Mustermann*.
   - Open it. The detail page shows the calculated days and the workflow timeline.
   - **Approve**, with an optional comment.
4. Switch back to **`employee`**:
   - the bell shows new notifications, and MailHog shows the same mail;
   - the leave balance dropped by the working days;
   - the request's timeline shows *Approve by Stefan Beispiel*;
   - in **Working Time › Monthly overview**, the approved days are credited (balance 0 on those days).
   - *Point out:* the time module was informed by an event through the transactional outbox, not by a direct table write.
5. *Optional:* as `supervisor`, open **Team Calendar** (the absence appears; the leave type is hidden for data protection) and **Delegations** (delegate absence approvals to Bettina Leitung for your holiday).

## Part 3 — Business travel (3 min)

1. As **`employee`**: **Travel › New travel request**, for example Berlin, a train trip next month, 480 EUR, cost centre `CC-2200`.
   - Add two funding shares: 60 % basic budget and 40 % project *Open Research Data*.
   - **Save and submit.** The cost centre was checked live in the finance system (mock-erp).
   - *Show the error case:* cost centre `CC-9000` is rejected as *not valid in the finance system*.
2. As **`supervisor`**: **My Tasks → Approve**.
3. As **`finance`** (Frieda Finanz): **My Tasks** now shows *Perform financial approval*. Open the trip and choose **Financial approval**. The status is **Authorized**.
4. Back as `employee`, reload the trip: **Travel ERP no.** `TRV-…` was returned by the travel ERP (outbox export). The export table shows *Processed*.
5. *Expenses (Scenario 4):*
   - open the seeded **Hamburg** trip, which is authorized and already took place, and choose **Mark trip completed**;
   - add a hotel and a train expense, upload any PDF as the receipt, and **Submit expense claim**;
   - log in as **`travel`** (travel office): **My Tasks → Accept and settle**;
   - the finance document number `FIN-…` and the settlement number appear shortly afterwards.

## Part 4 — Working time and correction (2 min)

1. As **`employee`**: **Working Time**.
   - Only **Clock in** is enabled. Click it; now **Start break** and **Clock out** are enabled.
   - Start and end a break, and watch the live account.
   - *Point out:* the server enforces the same sequence rules (`409 TIME_SEQUENCE_INVALID`).
2. **Monthly overview:** on the day marked **Missing entry**, choose **Correct → Add a missing entry → Clock out 17:00**, with a reason. The day shows *Correction pending*.
3. As **`supervisor`** (or `timeadmin`): **My Tasks → Approve**. As `employee`, the day is now complete and recalculated. The audit log shows the entries before and after the correction.

## Part 5 — Operations (3 min)

Log in as **`erpadmin`**.

1. **Dashboard:**
   - system health: database, mail, personnel ERP, finance ERP, travel ERP;
   - open integration errors and failed batch runs;
   - recent audit activity.
   - *Point out:* the ERP admin has no access to personnel data (a 403 on employee records).
2. **Batch Jobs:**
   - Run **organisation-sync**, then **employee-sync**. Both end **Partial**: open *Errors* to see the invalid records (unknown organisation unit, percentage 140, unknown parent unit).
   - A new employee *Lena Neu* was created, and a leaver was deactivated, not deleted.
   - Run **employee-sync** again: 0 records processed, because it is idempotent.
3. **Integration Monitor (Scenario 7, simulated failure):**
   - Switch on **Simulate outage** for *Finance ERP*.
   - In a second browser, as `employee` and `travel`, settle another expense claim (or submit a trip, which then fails with *system unavailable*).
   - The error appears under *Open errors* with its retry count. Pending exports wait in the outbox.
   - Switch the outage off and click **Retry**. The error is resolved, and the posting is created **once**: the same idempotency key returns the same document.
   - Alternative: `docker compose stop mock-erp`, then `docker compose start mock-erp`.
4. **Audit:** filter by entity type `AbsenceRequest`. You see submission and approval with actor, time and correlation ID. The audit log cannot be edited, which the database enforces.
5. *Optional:* **Reports** as `hradmin` (leave usage by unit, monthly working time, CSV download). Swagger UI is at http://localhost:4200/swagger-ui.html.
6. *Optional, languages:* choose **EN → Deutsch** in the top bar. The page reloads in German, including notifications, task titles, report columns and error messages from the server.

---

### Talking points for the review panel

- **Modular monolith** with verified boundaries (Spring Modulith and ArchUnit in the build); ports and adapters for every external system.
- **Consistency:** a workflow decision and the request status commit atomically. After-commit effects (time credit, mail, ERP export) go through a transactional outbox with idempotency keys.
- **Operations:**
  - batch jobs are idempotent, restartable and observable, one run per job at a time;
  - integration errors are persisted, retried and alerted;
  - correlation IDs run from the browser to mock-erp.
- **Security:**
  - session plus CSRF on a single origin;
  - server-side object-level checks;
  - an append-only audit log;
  - data minimisation (team calendar, directory search, aggregated reports).

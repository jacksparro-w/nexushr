# NexusHR API

Base URL: `http://localhost:8090`. JSON uses `application/json`. The refresh cookie is HTTP-only and is never included in JSON responses.

## `GET /api/health`

Public. Returns `{"status":"UP"}`.

## `POST /api/auth/login`

Public request:

```json
{"email":"admin@example.com","password":"your-password"}
```

Returns an access token and user identity, and sets `nexushr_refresh` as an HTTP-only, SameSite=Strict cookie.

## `POST /api/auth/refresh`

Public to the bearer-token filter; requires the valid refresh cookie. Rotates the refresh cookie and returns a new access token. A reused/expired token returns 401.

## `POST /api/auth/logout`

Public. Revokes the presented refresh credential when available and clears the refresh cookie.

## `GET /api/auth/me`

Requires `Authorization: Bearer <accessToken>`. Returns account id, email, and role names.

## `POST /api/auth/accounts`

Requires an access token with `ADMIN` or `HR` role. Request:

```json
{"email":"new.employee@example.com","password":"a-long-initial-password","roles":["EMPLOYEE"]}
```

Password length is 12–128 characters. If roles are omitted or empty, the account receives `EMPLOYEE`. HR may assign `EMPLOYEE` or `MANAGER`; only `ADMIN` may assign `HR` or `ADMIN`. Public registration is not exposed.

## Employee and department management

All endpoints below require a bearer access token unless noted. `ADMIN` and `HR` can manage departments, employee profiles, and document metadata. `MANAGER` can list direct reports and view their profiles. An employee can retrieve their own profile with `/api/employees/me` and their own document metadata. The work email on an employee profile must match the user's authentication email for self-service access.

### Departments

- `GET /api/departments` lists active departments. `?includeInactive=true` includes inactive departments.
- `POST /api/departments` creates a department (ADMIN/HR): `{"code":"ENG","name":"Engineering","description":"Product engineering","active":true,"managerId":null}`. `managerId` is optional.
- `PUT /api/departments/{id}` updates the name, description, active state, and optional `managerId`. The department code is immutable after creation.
- `DELETE /api/departments/{id}` deletes an unused department. Departments with employees must be deactivated instead.

### Employees

- `GET /api/employees/me` returns the employee profile associated with the signed-in email.
- `GET /api/employees/managers` (ADMIN/HR) lists active employee profiles whose matching login is enabled and has the `MANAGER` role.
- `GET /api/employees` returns a paginated Spring `Page` (`content`, `totalElements`, `totalPages`, `number`, `size`). Supports `q`, `departmentId`, `status`, `page` (default 0), and `size` (default 20, max 100). HR/admin receive the filtered directory; managers receive direct reports only; employees receive 403.
- `GET /api/employees/{id}` returns a profile to HR/admin, the employee themself, or their direct manager.
- `POST /api/employees` creates a profile (ADMIN/HR). Required fields: `employeeCode`, `firstName`, `lastName`, `workEmail`, `designation`, `departmentId`, and `hireDate` (`YYYY-MM-DD`). Optional fields: `phone`, `managerId`, `status` (`ACTIVE`, `INACTIVE`, `TERMINATED`), and `onboardingStatus` (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETE`).
- `PUT /api/employees/{id}` updates those profile fields (ADMIN/HR).
- `DELETE /api/employees/{id}` deletes a profile (ADMIN/HR); employees who have direct reports or attendance history cannot be deleted and should be deactivated instead.

Employee codes and work emails are unique. Duplicate values return 409, malformed/missing required fields return 400, and unknown department or manager IDs return 404. Departments must be active when assigned. A selected manager must be an active employee with an enabled login account that has the `MANAGER` role. Create the account with the same email as the profile before selecting them as a manager. Department manager assignment has the same requirement.

### Projects

- `GET /api/projects` lists projects for signed-in users.
- `POST /api/projects` creates a project (ADMIN/HR) with `code`, `name`, `departmentId`, and optional `managerId`, `description`, and `active`.
- `PUT /api/projects/{id}` updates its name, description, department, manager, and active state. The project code is immutable.

Project departments must be active. A project manager must be an active employee with an enabled matching login carrying the `MANAGER` role. Project-level employee membership is not currently modeled.

### Employee document metadata

- `GET /api/employees/{id}/documents` lists metadata for HR/admin or the employee themself.
- `POST /api/employees/{id}/documents` records metadata (ADMIN/HR), for example `{"documentType":"CONTRACT","fileName":"contract.pdf","contentType":"application/pdf","sizeBytes":24576}`.
- `DELETE /api/employees/{id}/documents/{documentId}` removes a metadata record (ADMIN/HR).

These endpoints store metadata only. The document binary is not uploaded or stored by this module.

## Attendance tracking

Attendance timestamps are stored as instants and work dates are calculated using ATTENDANCE_TIME_ZONE (default Asia/Kolkata). ATTENDANCE_WORKDAY_START defaults to 09:00; ATTENDANCE_GRACE_MINUTES defaults to 0. A clock-in after the configured start plus grace is late. Each employee can have one record per work date and only one open shift. Clock-out must occur after clock-in. Overnight clock-outs are supported; the work date remains the local date on which the shift started.

- POST /api/attendance/clock-in starts the signed-in employee's shift. Returns 409 for a duplicate daily record or an already-open shift.
- POST /api/attendance/clock-out closes the signed-in employee's open shift. Returns 409 if there is no open shift.
- GET /api/attendance/me/current returns the active shift (including an overnight shift) or today's completed record; returns 204 when no shift is open and no attendance is recorded today.
- GET /api/attendance/me?from=YYYY-MM-DD&to=YYYY-MM-DD lists the signed-in employee's records.
- GET /api/attendance/me/summary?month=YYYY-MM returns days worked, late days, completed days, and total completed work minutes.
- GET /api/attendance/summary?month=YYYY-MM returns the same monthly totals aggregated for the HR/Admin attendance dashboard.
- GET /api/attendance?from=YYYY-MM-DD&to=YYYY-MM-DD[&employeeId=UUID]&page=0&size=50 lists HR/admin attendance records as a page (size maximum 100).
- PATCH /api/attendance/{id}/correction is restricted to HR/admin. Requires corrected clockInAt, optional clockOutAt, and a reason (1–500 characters). Corrections keep the original work date and are stored in an append-only audit history.
- GET /api/attendance/{id}/audit returns the correction history and is restricted to HR/admin.

Date queries are limited to a 93-day inclusive window. Self-service attendance requires an active employee profile matching the signed-in account email. Managers do not get organization-wide attendance access in this module.

## Leave management

Leave requests are counted as weekdays (Monday–Friday); public holidays are not currently configured. A request must stay within one calendar year. The system reserves requested days while a request is pending, counts approved days as used, and releases days when a request is rejected or cancelled. Pending and approved requests cannot overlap for the same employee. HR/Admin allocates each employee's balance by leave type and calendar year. New installations seed `ANNUAL` and `SICK` leave types.

- `GET /api/leaves/types[?includeInactive=true]` lists active leave types; inactive types are included only when requested.
- `POST /api/leaves/types` and `PUT /api/leaves/types/{id}` create or update a type (HR/Admin). Fields: `code`, `name`, optional `description`, `paid`, and optional `active`.
- `GET /api/leaves/me/balances[?year=YYYY]` returns entitlement, used, pending, and available days for the signed-in employee.
- `GET /api/leaves/employees/{employeeId}/balances[?year=YYYY]` and `PUT /api/leaves/employees/{employeeId}/balances/{leaveTypeId}?year=YYYY` are restricted to HR/Admin. The update body is `{"entitledDays":20}` and cannot reduce entitlement below already approved plus pending days.
- `POST /api/leaves/me/requests` submits `{"leaveTypeId":"UUID","startDate":"YYYY-MM-DD","endDate":"YYYY-MM-DD","reason":"..."}`. It returns 409 for overlap or insufficient balance and 400 for invalid dates.
- `GET /api/leaves/me/requests` lists the signed-in employee's requests. `POST /api/leaves/me/requests/{id}/cancel` cancels only the owner's pending request.
- `GET /api/leaves/requests[?status=PENDING]` lists all requests to HR/Admin or direct-report requests to a manager.
- `POST /api/leaves/requests/{id}/decision` accepts `{"status":"APPROVED"|"REJECTED","note":"optional decision note"}`. HR/Admin may review any request; managers may review direct reports only.

Managers do not receive organization-wide leave access. Leave request and balance records prevent an employee profile from being deleted; deactivate that profile instead.

## Performance management

- `GET /api/performance/goals/me` lists the signed-in employee's goals. `POST /api/performance/goals` creates a goal with `title`, `description`, measurable `keyResult`, and a future/current `targetDate`.
- `PUT /api/performance/goals/{id}` updates an in-progress goal; its employee or direct manager may update it. `GET /api/performance/employees/{employeeId}/goals` is limited to the employee, their direct manager, HR, or Admin.
- `POST /api/performance/employees/{employeeId}/goals` creates a direct report goal for managers, or any employee goal for HR/Admin.
- `POST /api/performance/cycles` (HR/Admin) accepts `name`, `startDate`, and `endDate`; it creates a review for each active employee. `GET /api/performance/cycles` lists cycles; `POST /api/performance/cycles/{id}/close` closes a cycle.
- `GET /api/performance/reviews/me` returns the employee's own review history. `POST /api/performance/reviews/{id}/self-assessment` accepts `{"assessment":"..."}` once during an open cycle's date window.
- `GET /api/performance/reviews/team` lists a manager's direct reports. After an employee submits their self-assessment, the direct manager may submit `POST /api/performance/reviews/{id}/manager-review` with `{"rating":4,"feedback":"..."}`. Ratings must be integers from 1–5.
- `GET /api/performance/reviews` lists organization scorecards for HR/Admin. `GET /api/performance/reviews/{id}` is limited to the employee, direct manager, HR, or Admin.

Closed or out-of-window cycles reject assessment and feedback changes. Goal and review history prevent employee profile deletion; deactivate the profile instead.

## Payroll processing

This module uses a generic configurable formula: `gross = base salary + allowances`; `tax = round(gross × taxRatePercent / 100, 2)`; `net = gross − deductions − tax`. Monetary values use decimal arithmetic and HALF_UP rounding to two places. The flat tax percentage is a demonstration input; this implementation does not calculate jurisdiction-specific taxes, benefits, payroll filings, or statutory deductions. Monthly structures are not prorated: an employee is included only when the structure is active and effective on the first day of the payroll month. Generated payslips snapshot all calculation inputs and component details.

- `PUT /api/payroll/employees/{employeeId}/structure` creates or replaces an employee structure (HR/Admin): `{"currency":"USD","baseSalary":5000.00,"taxRatePercent":10.00,"effectiveFrom":"2026-01-01","active":true,"components":[{"name":"Housing","type":"ALLOWANCE","amount":1000.00},{"name":"Insurance","type":"DEDUCTION","amount":500.00}]}`. Currency is a three-letter code; amounts cannot be negative; tax rate is 0–100%.
- `GET /api/payroll/employees/{employeeId}/structure` and `GET /api/payroll/structures` expose structures to HR/Admin only.
- `POST /api/payroll/runs?month=YYYY-MM` generates a DRAFT run for active employees with effective structures. A database uniqueness constraint allows one run per month; duplicate months return 409. Future months and runs with no eligible employees are rejected.
- `GET /api/payroll/runs` lists runs (HR/Admin). `GET /api/payroll/runs/{runId}/payslips?page=0&size=50` returns paginated slips (maximum size 100).
- `POST /api/payroll/runs/{runId}/approve` and `POST /api/payroll/runs/{runId}/mark-paid` are restricted to ADMIN. State transitions are DRAFT → APPROVED → PAID; invalid transitions return 409.
- `GET /api/payroll/runs/{runId}/export` exports the run as CSV (HR/Admin).
- `GET /api/payroll/me/payslips` and `GET /api/payroll/me/payslips/{payslipId}` let employees retrieve only their own history and slips.
- `POST /api/payroll/payslips/{payslipId}/corrections` is HR/Admin-only and allowed only while the run is DRAFT. It accepts named ALLOWANCE/DEDUCTION adjustments, an optional tax-rate override, and a required reason. Corrections append to the component snapshot and recalculate amounts. `GET /api/payroll/payslips/{payslipId}/corrections` returns the before/after correction history.

Salary structures or payslip history prevent employee profile deletion; deactivate the profile instead. Generated payslips remain snapshots if the current salary structure later changes.

## Notifications and communication

- `GET /api/notifications` returns up to the latest 100 notifications for the signed-in account. `GET /api/notifications/unread-count` returns `{"count":n}`.
- `PATCH /api/notifications/{id}/read` marks one notification read and returns 404 if it does not belong to the caller. `POST /api/notifications/read-all` marks the caller's notifications read.
- `GET /api/announcements` lists the latest 50 company announcements. `POST /api/announcements` (HR/Admin) accepts `{"title":"...","body":"..."}` and publishes an in-app alert and queued email to each account.
- Connect to `ws://localhost:8090/ws/notifications` (or `wss` in HTTPS deployments). Send `{"type":"AUTH","accessToken":"<access token>"}` as the first frame. The server responds with `{"type":"READY"}` and then sends `{"type":"NOTIFICATION","notification":{...}}` frames. Invalid tokens close the socket; no notifications are sent before authentication.
- Leave approval/rejection alerts go to the requesting employee. Payroll approval/payment alerts go to employees represented by the run's payslips. Notification records are persisted before WebSocket delivery.
- Email is optional and disabled by default. Enable it with `EMAIL_NOTIFICATIONS_ENABLED=true` and the `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` environment variables. Failed messages retry with capped exponential backoff for up to eight attempts; after that they remain marked dead in `email_outbox`.

## Module 9 — Workforce intelligence

- `GET /api/intelligence/workforce` — ADMIN/HR aggregate active headcount, last-30-day attendance and lateness records, and goal counts.
- `POST /api/intelligence/questions` — ADMIN/HR natural-language question, JSON body `{ "question": "..." }`, 1–500 characters. Uses Spring AI only when `AI_ENABLED=true`; otherwise returns a deterministic fallback. The prompt contains aggregate metrics only.
- `GET /api/intelligence/attrition/{employeeId}` — ADMIN/HR advisory baseline score, risk band, contributing factors, and model version. Fewer than five attendance observations returns `INSUFFICIENT_DATA`.
- `POST /api/intelligence/skill-gaps` — ADMIN/HR comparison of aggregate `requiredSkills` and `currentSkills` arrays; returns coverage percentage, matches, and missing skills. It accepts no employee identities.

All responses identify data scope and include a decision-support disclaimer. Do not use baseline estimates to make employment decisions. Predictive performance metrics require a separately validated, representative dataset before production use.

## Module 10 — Security hardening

- `GET /api/security/audit` — ADMIN only; returns the latest 100 metadata-only audit events for sensitive writes.
- Login, refresh, account creation, and workforce-intelligence routes are rate limited by remote client address. A `429` response includes `Retry-After`.
- API errors have `timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors` fields. Unexpected exceptions return a generic message; details stay in server logs.
# Module 9 — Workforce intelligence

- `GET /api/intelligence/workforce` — ADMIN/HR aggregate active headcount, last-30-day attendance and lateness records, and goal counts.
- `POST /api/intelligence/questions` — ADMIN/HR natural-language question, JSON body `{ "question": "..." }`, 1–500 characters. Uses Spring AI only when `AI_ENABLED=true`; otherwise returns a deterministic fallback. The prompt contains aggregate metrics only.
- `GET /api/intelligence/attrition/{employeeId}` — ADMIN/HR advisory baseline score, risk band, contributing factors, and model version. Fewer than five attendance observations returns `INSUFFICIENT_DATA`.

All responses identify data scope and include a decision-support disclaimer. Do not use baseline estimates to make employment decisions. Predictive performance metrics require a separately validated, representative dataset before production use.

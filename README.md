# NexusHR

NexusHR is a modular Spring Boot application with a React/TypeScript client. Modules 1–9 provide authentication, employee and department management, attendance, leave, payroll, performance, dashboards, notifications, and workforce intelligence.

## Requirements

- Java 21
- Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop with Docker Compose

## Local setup

1. Copy `.env.example` to `.env` and replace every `REPLACE_WITH_...` value with a local secret. Use a unique JWT secret of at least 32 bytes and a strong bootstrap admin password.
2. Start PostgreSQL with `docker compose up -d postgres`. Docker Compose reads its database credentials from the root `.env`.
3. Run the backend from `backend`: `mvn spring-boot:run`. Spring Boot loads the root `.env` automatically; no credentials need to be entered in terminal commands.
4. Run the frontend from `frontend`: `npm install` then `npm run dev`.
5. Sign in with the bootstrap account at `http://localhost:5173`. The database schema is created by Flyway on startup.
6. Inspect and try backend endpoints in Swagger UI at `http://localhost:8090/swagger-ui/index.html`. The OpenAPI JSON is available at `http://localhost:8090/v3/api-docs`. Use **Authorize** to enter a bearer access token for protected account endpoints.

Do not use the sample local credentials outside a development machine. Set `COOKIE_SECURE=true` when serving the API over HTTPS.

## Module 1 behavior

- Roles are `ADMIN`, `HR`, `MANAGER`, and `EMPLOYEE`.
- Passwords are hashed with Argon2id through Spring Security.
- Login returns a 15-minute bearer access token. The 14-day refresh credential is stored only as a hash in PostgreSQL and delivered in an HTTP-only, SameSite=Strict cookie.
- Refresh rotates credentials under a database row lock. Reuse of a rotated credential revokes the user's outstanding refresh tokens.
- Account creation is restricted to `ADMIN` and `HR`; HR can create employee and manager accounts, while only an administrator can assign HR/admin roles.
- The initial administrator is provisioned once from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env`; public self-registration is disabled.

## API summary

See [docs/api-specification.md](docs/api-specification.md) for request/response shapes and [docs/architecture.md](docs/architecture.md) for the current module boundaries.

## Module 2 behavior

- Departments and employee profiles are managed by ADMIN/HR roles, with unique department codes, employee IDs, and work emails.
- The directory supports case-insensitive name/email/ID search, department/status filters, and pagination. Managers see direct reports; employees see their own profile.
- Profiles include department, designation, manager, employment status, hire date, and onboarding status.
- Document records contain metadata only; binary upload and storage are not included.
- Employee login accounts are created separately through the authentication account endpoint. Match the account email to the employee profile's work email to enable self-service profile access.

## Module 3 behavior

- Employees can clock in and out, review monthly attendance history, and see backend-calculated work summaries.
- HR/Admin dashboards show the same server-calculated monthly totals alongside a paginated organization-wide attendance list.
- Attendance date and lateness rules use configurable ATTENDANCE_TIME_ZONE, ATTENDANCE_WORKDAY_START, and ATTENDANCE_GRACE_MINUTES values.
- The backend enforces one daily record and one open shift per employee. Overnight clock-outs are supported and remain on the shift's starting work date.
- HR and Admin can review attendance and correct records with a required reason; every correction retains before/after timestamps and the correcting account in an audit log.

## Module 4 behavior

- Employees can submit weekday-based leave requests, review their request history, and cancel pending requests.
- Pending requests reserve leave days; approved requests consume those days, while rejected or cancelled requests release the reservation.
- HR/Admin can configure leave types and allocate employee balances by calendar year. Annual and sick leave types are seeded by migration.
- Managers can review requests from direct reports. HR/Admin can review organization-wide requests; every decision stores the reviewer, timestamp, status, and optional decision note.
- Overlapping pending/approved requests, requests beyond available balance, past dates, cross-year requests, and weekends-only requests are rejected.

## Module 5 behavior

- HR/Admin can configure a monthly salary structure per active employee with a currency code, base salary, effective date, flat tax rate, and itemized allowances and deductions.
- Payroll runs snapshot salary inputs into payslips. A unique month prevents duplicate runs; later salary edits do not rewrite generated slips.
- HR/Admin can generate and correct draft runs. Admin approves and marks runs paid. Draft corrections require a reason and retain before/after amounts and component details.
- HR/Admin can inspect runs and export CSV. Employees can access their own payslip history.
- The generic demo calculation is gross = base + allowances, tax = configured flat rate × gross, and net = gross − deductions − tax, rounded to two decimals using HALF_UP. It does not implement jurisdiction-specific statutory tax, deductions, benefits, or payroll compliance.

## Module 6 behavior

- Employees can create measurable goals, view their goal list, and mark in-progress goals complete. HR/Admin and a direct manager can view goal details; managers can only create goals for direct reports.
- HR/Admin can open and close review cycles with explicit start and end dates. Opening a cycle creates one review record for each active employee.
- Employees submit one self-assessment during the open cycle window. Their assigned direct manager can then provide one rating from 1–5 and written feedback before the deadline.
- Employees see their own review history and completed feedback. Managers see direct-report reviews; HR/Admin can inspect organization-wide scorecards. Cross-employee access is denied.
- Closed or out-of-window reviews cannot be changed. Reviews and goals prevent employee deletion; deactivate the employee instead.

## Module 7 behavior

- The dashboard aggregates attendance, leave, payroll, and goal data from the backend for employees; managers see direct-report activity, and HR/Admin see organization-wide metrics.
- Dashboard filters use the selected month. CSV export is generated from the loaded data and loading/error/empty states are shown in the UI.
- Dashboard metrics use live API records; the generic visual summaries should be checked against source records for operational reporting.

## Module 8 behavior

- Users receive persistent in-app notifications for leave decisions and payroll approval/payment changes. Notifications are scoped to the signed-in account and support unread counts, individual read state, and mark-all-read.
- Notifications are pushed over an authenticated WebSocket at `/ws/notifications`; the browser reconnects with a refreshed access token and can fall back to the REST notification history.
- HR/Admin can publish company announcements. Announcements are saved and create an in-app alert plus an email outbox entry for each account.
- Leave and payroll alerts are published after the business transaction commits, so notification or email problems do not roll back the leave decision or payroll status change.
- Email delivery is disabled by default. Configure `EMAIL_NOTIFICATIONS_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` in the local `.env` to enable SMTP. Pending email is retried with exponential backoff up to eight attempts; failed items are retained for inspection.
- The email outbox stores recipient email, message content, and delivery state. Use an approved SMTP provider and review retention/access requirements before production use.

## Module 9 behavior

- ADMIN/HR can view aggregate workforce indicators and ask natural-language questions. Only aggregated counts are sent to the optional AI model; employee identifiers and free-text records are excluded.
- If the AI provider is disabled or unavailable, the API returns a clearly labeled deterministic summary.
- A baseline attrition estimate is available per active employee. It reports contributing factors and an insufficient-data state; it is decision support only and must never drive automated employment decisions.
- HR can compare aggregate required/current skill lists for coverage and gaps. An employee-level skills inventory is not yet part of the schema, so callers must provide a validated aggregate inventory.
- Enable Spring AI with `AI_ENABLED=true`, `OPENAI_API_KEY`, and optional `AI_MODEL` in `.env`. Keep the provider disabled when no approved key/provider is configured.
- Skill-gap modeling is not enabled until the application has a validated skills inventory; goals are not treated as skills.

## Module 10 behavior

- API errors use a consistent JSON structure and avoid returning unexpected exception details.
- Login, refresh, account creation, and AI endpoints have in-memory per-IP rate limits. Use a shared Redis-backed limiter before running multiple production instances.
- Security headers deny framing, enable MIME sniffing protection and apply HSTS on HTTPS requests. CORS accepts only the configured `FRONTEND_ORIGIN`.
- Mutating employee, payroll, account, and workforce-intelligence requests are recorded in `security_audit_log` with actor ID when authenticated, route, method, status, and timestamp. ADMIN can view the latest 100 events at `/api/security/audit`. Request bodies, credentials, and employee details are not copied into this audit log.
- `.github/workflows/dependency-review.yml` blocks pull requests when dependency review identifies high-severity vulnerabilities. Set `COOKIE_SECURE=true` and `SWAGGER_ENABLED=false` for HTTPS production deployments; keep all secrets in the deployment secret store.

## Module 11 behavior

- Docker Compose builds the Spring Boot backend and unprivileged Nginx/React frontend and runs them with PostgreSQL and Redis. App/database ports bind to loopback for the local demo.
- Backend health checks gate frontend startup. Management health and Prometheus metrics use a separate internal-only port. The optional monitoring overlay provisions Prometheus and Grafana with an HTTP/JVM dashboard.
- Backend console logs use JSON. GitHub Actions verifies backend tests, builds the frontend, and builds both container images. Dependency review runs separately on pull requests.
- PowerShell scripts create and restore custom-format PostgreSQL backups. Restore requires explicit typed confirmation and stops the backend during the operation.
- Redis is deployed but not yet connected to application features; the rate limiter remains in-memory. Cloud deployment, TLS termination, and off-host backup storage require a separately provisioned hosting target.

## Current verification status

Authentication and authorization have backend unit and integration coverage. Integration tests use an isolated H2 database; PostgreSQL startup and browser integration depend on Docker and are not covered by that test run.

# Architecture (current phase)

NexusHR starts as a modular monolith. Business code is grouped by feature so later HR modules can be added without prematurely operating multiple services.

```text
frontend (React + TypeScript)
  └── same-origin API client contract → Spring Boot REST API
                                          ├── auth: accounts, roles, tokens
                                          ├── security: JWT filter and access rules
                                          ├── common: health and shared concerns
                                          ├── attendance: daily records and correction audit
                                          ├── leave: leave types, balances, requests, and approval workflow
                                          ├── payroll: salary structures, monthly runs, payslips, and correction history
                                          ├── performance: measurable goals, review cycles, self-assessments, and scorecards
                                          └── PostgreSQL via Spring Data JPA
                                               └── Flyway owns schema changes
```

The browser keeps the short-lived access token in memory. Refresh credentials are HTTP-only cookies and only their SHA-256 hashes are persisted. Password hashes use Argon2. Hibernate validates the Flyway schema rather than generating tables. PostgreSQL is the only required local infrastructure service in this phase.

## Module 1 boundaries

- `auth`: account and role persistence, login, account creation, refresh rotation, logout.
- `security`: stateless bearer-token authentication, Argon2 password encoder, CORS, and method-level role checks.
- `common`: a public health endpoint.

## Module 3 boundaries

- `attendance`: employee clock-in/out, work-date and lateness policy, attendance summaries, HR/Admin corrections, and immutable before/after correction records.
- `leave`: configurable leave types and yearly balances, weekday-based requests, balance reservation, cancellation, manager review of direct reports, and HR/Admin oversight.
- `payroll`: configurable compensation inputs, traceable monthly calculation snapshots, controlled approval/payment transitions, employee payslips, CSV export, and auditable draft corrections.
- `performance`: employee goals, manager/direct-report feedback, deadline-bound review cycles, and role-scoped review histories.

Refresh rotation takes a pessimistic row lock, so simultaneous refresh requests cannot both redeem the same token. An access token remains valid until its short expiration; logout revokes refresh ability but does not blacklist already-issued access tokens.

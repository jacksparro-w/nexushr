CREATE TABLE leave_types (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    paid BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE leave_balances (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    leave_type_id UUID NOT NULL REFERENCES leave_types(id),
    leave_year INTEGER NOT NULL,
    entitled_days NUMERIC(6, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_leave_balance_employee_type_year UNIQUE (employee_id, leave_type_id, leave_year),
    CONSTRAINT chk_leave_balance_entitled CHECK (entitled_days >= 0)
);

CREATE TABLE leave_requests (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    leave_type_id UUID NOT NULL REFERENCES leave_types(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days NUMERIC(6, 2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_note VARCHAR(1000),
    reviewed_by VARCHAR(320),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_request_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_leave_request_days CHECK (days > 0),
    CONSTRAINT chk_leave_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_leave_requests_employee_dates ON leave_requests(employee_id, start_date, end_date);
CREATE INDEX idx_leave_requests_status_dates ON leave_requests(status, start_date, end_date);
CREATE INDEX idx_leave_balances_employee_year ON leave_balances(employee_id, leave_year);

INSERT INTO leave_types (id, code, name, description, paid, active) VALUES
    ('4a883c1f-8116-4d2a-8a12-3ae3b39241c1', 'ANNUAL', 'Annual leave', 'Annual paid time off', TRUE, TRUE),
    ('d20b0f5b-5b27-46b6-a50c-31a74c90e8c2', 'SICK', 'Sick leave', 'Medical and recovery leave', TRUE, TRUE);

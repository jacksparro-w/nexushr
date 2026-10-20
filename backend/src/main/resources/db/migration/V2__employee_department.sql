CREATE TABLE departments (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE employees (
    id UUID PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    work_email VARCHAR(320) NOT NULL UNIQUE,
    phone VARCHAR(40),
    designation VARCHAR(120) NOT NULL,
    department_id UUID NOT NULL REFERENCES departments(id),
    manager_id UUID REFERENCES employees(id),
    employment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    onboarding_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    hire_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_status CHECK (employment_status IN ('ACTIVE', 'INACTIVE', 'TERMINATED')),
    CONSTRAINT chk_employee_onboarding CHECK (onboarding_status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETE'))
);
CREATE INDEX idx_employees_department ON employees(department_id);
CREATE INDEX idx_employees_manager ON employees(manager_id);

CREATE TABLE employee_documents (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_employee_document_size CHECK (size_bytes >= 0)
);
CREATE INDEX idx_employee_documents_employee ON employee_documents(employee_id);

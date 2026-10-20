CREATE TABLE payroll_structures (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL UNIQUE REFERENCES employees(id),
    currency VARCHAR(3) NOT NULL,
    base_salary NUMERIC(14, 2) NOT NULL,
    tax_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_payroll_base_salary CHECK (base_salary >= 0),
    CONSTRAINT chk_payroll_tax_rate CHECK (tax_rate_percent >= 0 AND tax_rate_percent <= 100)
);

CREATE TABLE payroll_components (
    id UUID PRIMARY KEY,
    structure_id UUID NOT NULL REFERENCES payroll_structures(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_payroll_component_type CHECK (component_type IN ('ALLOWANCE', 'DEDUCTION')),
    CONSTRAINT chk_payroll_component_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payroll_components_structure ON payroll_components(structure_id);

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY,
    payroll_month VARCHAR(7) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_by VARCHAR(320) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    approved_by VARCHAR(320),
    approved_at TIMESTAMP WITH TIME ZONE,
    paid_by VARCHAR(320),
    paid_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_payroll_run_status CHECK (status IN ('DRAFT', 'APPROVED', 'PAID'))
);

CREATE TABLE payslips (
    id UUID PRIMARY KEY,
    payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id),
    employee_id UUID NOT NULL REFERENCES employees(id),
    currency VARCHAR(3) NOT NULL,
    base_salary NUMERIC(14, 2) NOT NULL,
    allowance_total NUMERIC(14, 2) NOT NULL,
    deduction_total NUMERIC(14, 2) NOT NULL,
    gross_pay NUMERIC(14, 2) NOT NULL,
    tax_rate_percent NUMERIC(5, 2) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL,
    net_pay NUMERIC(14, 2) NOT NULL,
    component_details TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslip_employee_month UNIQUE (employee_id, payroll_run_id),
    CONSTRAINT chk_payslip_amounts CHECK (
        base_salary >= 0 AND allowance_total >= 0 AND deduction_total >= 0 AND
        gross_pay >= 0 AND tax_rate_percent >= 0 AND tax_rate_percent <= 100 AND
        tax_amount >= 0 AND net_pay >= 0
    )
);
CREATE INDEX idx_payslips_employee ON payslips(employee_id, payroll_run_id);

CREATE TABLE payslip_corrections (
    id UUID PRIMARY KEY,
    payslip_id UUID NOT NULL REFERENCES payslips(id),
    previous_base_salary NUMERIC(14, 2) NOT NULL,
    previous_allowance_total NUMERIC(14, 2) NOT NULL,
    previous_deduction_total NUMERIC(14, 2) NOT NULL,
    previous_gross_pay NUMERIC(14, 2) NOT NULL,
    previous_tax_rate_percent NUMERIC(5, 2) NOT NULL,
    previous_tax_amount NUMERIC(14, 2) NOT NULL,
    previous_net_pay NUMERIC(14, 2) NOT NULL,
    previous_component_details TEXT NOT NULL,
    corrected_base_salary NUMERIC(14, 2) NOT NULL,
    corrected_allowance_total NUMERIC(14, 2) NOT NULL,
    corrected_deduction_total NUMERIC(14, 2) NOT NULL,
    corrected_gross_pay NUMERIC(14, 2) NOT NULL,
    corrected_tax_rate_percent NUMERIC(5, 2) NOT NULL,
    corrected_tax_amount NUMERIC(14, 2) NOT NULL,
    corrected_net_pay NUMERIC(14, 2) NOT NULL,
    corrected_component_details TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    corrected_by VARCHAR(320) NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_payslip_corrections_record ON payslip_corrections(payslip_id, corrected_at DESC);

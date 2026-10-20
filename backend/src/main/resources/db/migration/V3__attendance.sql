CREATE TABLE attendance_records (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    work_date DATE NOT NULL,
    clock_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    clock_out_at TIMESTAMP WITH TIME ZONE,
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_attendance_employee_work_date UNIQUE (employee_id, work_date),
    CONSTRAINT chk_attendance_clock_range CHECK (clock_out_at IS NULL OR clock_out_at > clock_in_at)
);
CREATE INDEX idx_attendance_date ON attendance_records(work_date DESC);
CREATE INDEX idx_attendance_employee_date ON attendance_records(employee_id, work_date DESC);

CREATE TABLE attendance_audits (
    id UUID PRIMARY KEY,
    attendance_id UUID NOT NULL REFERENCES attendance_records(id),
    previous_clock_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    previous_clock_out_at TIMESTAMP WITH TIME ZONE,
    corrected_clock_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    corrected_clock_out_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(500) NOT NULL,
    corrected_by VARCHAR(320) NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_attendance_audit_old_range CHECK (previous_clock_out_at IS NULL OR previous_clock_out_at > previous_clock_in_at),
    CONSTRAINT chk_attendance_audit_new_range CHECK (corrected_clock_out_at IS NULL OR corrected_clock_out_at > corrected_clock_in_at)
);
CREATE INDEX idx_attendance_audits_record ON attendance_audits(attendance_id, corrected_at DESC);

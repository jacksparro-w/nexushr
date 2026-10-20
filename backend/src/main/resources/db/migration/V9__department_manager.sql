ALTER TABLE departments
    ADD COLUMN manager_id UUID REFERENCES employees(id);

CREATE INDEX idx_departments_manager ON departments(manager_id);

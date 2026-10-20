CREATE TABLE projects (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    department_id UUID NOT NULL REFERENCES departments(id),
    manager_id UUID REFERENCES employees(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_department ON projects(department_id);
CREATE INDEX idx_projects_manager ON projects(manager_id);

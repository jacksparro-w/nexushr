CREATE TABLE performance_goals (
  id UUID PRIMARY KEY,
  employee_id UUID NOT NULL REFERENCES employees(id),
  title VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  key_result VARCHAR(500) NOT NULL,
  target_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_goal_status CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX ix_performance_goals_employee_target ON performance_goals(employee_id, target_date);

CREATE TABLE performance_review_cycles (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_by VARCHAR(320) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_review_cycle_dates CHECK (end_date >= start_date),
  CONSTRAINT ck_review_cycle_status CHECK (status IN ('OPEN','CLOSED'))
);

CREATE TABLE performance_reviews (
  id UUID PRIMARY KEY,
  cycle_id UUID NOT NULL REFERENCES performance_review_cycles(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  self_assessment VARCHAR(3000),
  self_submitted_at TIMESTAMP WITH TIME ZONE,
  manager_rating INTEGER,
  manager_feedback VARCHAR(3000),
  manager_id UUID REFERENCES employees(id),
  completed_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_performance_review_cycle_employee UNIQUE (cycle_id, employee_id),
  CONSTRAINT ck_performance_review_rating CHECK (manager_rating IS NULL OR manager_rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_performance_reviews_employee_cycle ON performance_reviews(employee_id, cycle_id);

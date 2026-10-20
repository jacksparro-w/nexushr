package com.nexushr.project;

import com.nexushr.department.Department;
import com.nexushr.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 30)
  private String code;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_id")
  private Employee manager;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Project() {}

  public Project(String code, String name, String description, Department department,
      Employee manager, boolean active) {
    this.code = code.trim().toUpperCase();
    update(name, description, department, manager, active);
  }

  public void update(String name, String description, Department department, Employee manager,
      boolean active) {
    this.name = name.trim();
    this.description = description;
    this.department = department;
    this.manager = manager;
    this.active = active;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public Department getDepartment() { return department; }
  public Employee getManager() { return manager; }
  public boolean isActive() { return active; }
}

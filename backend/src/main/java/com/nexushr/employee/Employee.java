package com.nexushr.employee;

import com.nexushr.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class Employee {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "employee_code", nullable = false, unique = true, length = 30) private String employeeCode;
  @Column(name = "first_name", nullable = false, length = 100) private String firstName;
  @Column(name = "last_name", nullable = false, length = 100) private String lastName;
  @Column(name = "work_email", nullable = false, unique = true, length = 320) private String workEmail;
  @Column(length = 40) private String phone;
  @Column(nullable = false, length = 120) private String designation;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false) private Department department;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "manager_id") private Employee manager;
  @Enumerated(EnumType.STRING) @Column(name = "employment_status", nullable = false, length = 30) private EmploymentStatus status;
  @Enumerated(EnumType.STRING) @Column(name = "onboarding_status", nullable = false, length = 30) private OnboardingStatus onboardingStatus;
  @Column(name = "hire_date", nullable = false) private LocalDate hireDate;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected Employee() {}

  public Employee(EmployeeDtos.SaveRequest request, Department department, Employee manager) {
    update(request, department, manager);
  }

  public void update(EmployeeDtos.SaveRequest request, Department department, Employee manager) {
    employeeCode = request.employeeCode().trim().toUpperCase();
    firstName = request.firstName().trim();
    lastName = request.lastName().trim();
    workEmail = request.workEmail().trim().toLowerCase();
    phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
    designation = request.designation().trim();
    this.department = department;
    this.manager = manager;
    status = request.status() == null ? EmploymentStatus.ACTIVE : request.status();
    onboardingStatus = request.onboardingStatus() == null ? OnboardingStatus.NOT_STARTED : request.onboardingStatus();
    hireDate = request.hireDate();
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getEmployeeCode() { return employeeCode; }
  public String getFirstName() { return firstName; }
  public String getLastName() { return lastName; }
  public String getWorkEmail() { return workEmail; }
  public String getPhone() { return phone; }
  public String getDesignation() { return designation; }
  public Department getDepartment() { return department; }
  public Employee getManager() { return manager; }
  public EmploymentStatus getStatus() { return status; }
  public OnboardingStatus getOnboardingStatus() { return onboardingStatus; }
  public LocalDate getHireDate() { return hireDate; }
}

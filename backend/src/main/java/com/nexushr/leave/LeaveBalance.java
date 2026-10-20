package com.nexushr.leave;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leave_balances", uniqueConstraints =
    @UniqueConstraint(name = "uq_leave_balance_employee_type_year", columnNames = {"employee_id", "leave_type_id", "leave_year"}))
public class LeaveBalance {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) private Employee employee;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "leave_type_id", nullable = false) private LeaveType leaveType;
  @Column(name = "leave_year", nullable = false) private int year;
  @Column(name = "entitled_days", nullable = false, precision = 6, scale = 2) private BigDecimal entitledDays;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected LeaveBalance() {}

  LeaveBalance(Employee employee, LeaveType leaveType, int year, BigDecimal entitledDays) {
    this.employee = employee;
    this.leaveType = leaveType;
    this.year = year;
    this.entitledDays = entitledDays;
  }

  void setEntitledDays(BigDecimal entitledDays) { this.entitledDays = entitledDays; this.updatedAt = Instant.now(); }
  public UUID getId() { return id; }
  public Employee getEmployee() { return employee; }
  public LeaveType getLeaveType() { return leaveType; }
  public int getYear() { return year; }
  public BigDecimal getEntitledDays() { return entitledDays; }
}

package com.nexushr.attendance;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_records", uniqueConstraints =
    @UniqueConstraint(name = "uq_attendance_employee_work_date", columnNames = {"employee_id", "work_date"}))
public class AttendanceRecord {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) private Employee employee;
  @Column(name = "work_date", nullable = false) private LocalDate workDate;
  @Column(name = "clock_in_at", nullable = false) private Instant clockInAt;
  @Column(name = "clock_out_at") private Instant clockOutAt;
  @Column(name = "is_late", nullable = false) private boolean late;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected AttendanceRecord() {}

  AttendanceRecord(Employee employee, LocalDate workDate, Instant clockInAt, boolean late) {
    this.employee = employee;
    this.workDate = workDate;
    this.clockInAt = clockInAt;
    this.late = late;
  }

  public void clockOut(Instant clockOutAt) {
    this.clockOutAt = clockOutAt;
    this.updatedAt = Instant.now();
  }

  public void correct(Instant clockInAt, Instant clockOutAt, boolean late) {
    this.clockInAt = clockInAt;
    this.clockOutAt = clockOutAt;
    this.late = late;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public Employee getEmployee() { return employee; }
  public LocalDate getWorkDate() { return workDate; }
  public Instant getClockInAt() { return clockInAt; }
  public Instant getClockOutAt() { return clockOutAt; }
  public boolean isLate() { return late; }
}

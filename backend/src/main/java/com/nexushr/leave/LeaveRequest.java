package com.nexushr.leave;

import com.nexushr.employee.Employee;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) private Employee employee;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "leave_type_id", nullable = false) private LeaveType leaveType;
  @Column(name = "start_date", nullable = false) private LocalDate startDate;
  @Column(name = "end_date", nullable = false) private LocalDate endDate;
  @Column(nullable = false, precision = 6, scale = 2) private BigDecimal days;
  @Column(nullable = false, length = 1000) private String reason;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LeaveStatus status = LeaveStatus.PENDING;
  @Column(name = "decision_note", length = 1000) private String decisionNote;
  @Column(name = "reviewed_by", length = 320) private String reviewedBy;
  @Column(name = "reviewed_at") private Instant reviewedAt;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected LeaveRequest() {}

  LeaveRequest(Employee employee, LeaveType leaveType, LeaveDtos.ApplyRequest request, BigDecimal days) {
    this.employee = employee;
    this.leaveType = leaveType;
    this.startDate = request.startDate();
    this.endDate = request.endDate();
    this.days = days;
    this.reason = request.reason().trim();
  }

  void decide(LeaveStatus decision, String note, String reviewer) {
    status = decision;
    decisionNote = note == null || note.isBlank() ? null : note.trim();
    reviewedBy = reviewer;
    reviewedAt = Instant.now();
    updatedAt = Instant.now();
  }

  void cancel() { status = LeaveStatus.CANCELLED; updatedAt = Instant.now(); }
  public UUID getId() { return id; }
  public Employee getEmployee() { return employee; }
  public LeaveType getLeaveType() { return leaveType; }
  public LocalDate getStartDate() { return startDate; }
  public LocalDate getEndDate() { return endDate; }
  public BigDecimal getDays() { return days; }
  public String getReason() { return reason; }
  public LeaveStatus getStatus() { return status; }
  public String getDecisionNote() { return decisionNote; }
  public String getReviewedBy() { return reviewedBy; }
  public Instant getReviewedAt() { return reviewedAt; }
  public Instant getCreatedAt() { return createdAt; }
}

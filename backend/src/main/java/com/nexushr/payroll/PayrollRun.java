package com.nexushr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "payroll_runs")
public class PayrollRun {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "payroll_month", nullable = false, unique = true, length = 7) private String payrollMonth;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PayrollRunStatus status = PayrollRunStatus.DRAFT;
  @Column(name = "generated_by", nullable = false, length = 320) private String generatedBy;
  @Column(name = "generated_at", nullable = false, updatable = false) private Instant generatedAt = Instant.now();
  @Column(name = "approved_by", length = 320) private String approvedBy;
  @Column(name = "approved_at") private Instant approvedAt;
  @Column(name = "paid_by", length = 320) private String paidBy;
  @Column(name = "paid_at") private Instant paidAt;

  protected PayrollRun() {}

  PayrollRun(YearMonth month, String generatedBy) {
    this.payrollMonth = month.toString();
    this.generatedBy = generatedBy;
  }

  void approve(String actor) {
    status = PayrollRunStatus.APPROVED;
    approvedBy = actor;
    approvedAt = Instant.now();
  }

  void pay(String actor) {
    status = PayrollRunStatus.PAID;
    paidBy = actor;
    paidAt = Instant.now();
  }

  public UUID getId() { return id; }
  public YearMonth getMonth() { return YearMonth.parse(payrollMonth); }
  public PayrollRunStatus getStatus() { return status; }
  public String getGeneratedBy() { return generatedBy; }
  public Instant getGeneratedAt() { return generatedAt; }
  public String getApprovedBy() { return approvedBy; }
  public Instant getApprovedAt() { return approvedAt; }
  public String getPaidBy() { return paidBy; }
  public Instant getPaidAt() { return paidAt; }
}

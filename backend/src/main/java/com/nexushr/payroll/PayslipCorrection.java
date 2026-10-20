package com.nexushr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payslip_corrections")
public class PayslipCorrection {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payslip_id", nullable = false) private Payslip payslip;
  @Column(name = "previous_base_salary", nullable = false, precision = 14, scale = 2) private BigDecimal previousBaseSalary;
  @Column(name = "previous_allowance_total", nullable = false, precision = 14, scale = 2) private BigDecimal previousAllowanceTotal;
  @Column(name = "previous_deduction_total", nullable = false, precision = 14, scale = 2) private BigDecimal previousDeductionTotal;
  @Column(name = "previous_gross_pay", nullable = false, precision = 14, scale = 2) private BigDecimal previousGrossPay;
  @Column(name = "previous_tax_rate_percent", nullable = false, precision = 5, scale = 2) private BigDecimal previousTaxRatePercent;
  @Column(name = "previous_tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal previousTaxAmount;
  @Column(name = "previous_net_pay", nullable = false, precision = 14, scale = 2) private BigDecimal previousNetPay;
  @Column(name = "previous_component_details", nullable = false, columnDefinition = "TEXT") private String previousComponentDetails;
  @Column(name = "corrected_base_salary", nullable = false, precision = 14, scale = 2) private BigDecimal correctedBaseSalary;
  @Column(name = "corrected_allowance_total", nullable = false, precision = 14, scale = 2) private BigDecimal correctedAllowanceTotal;
  @Column(name = "corrected_deduction_total", nullable = false, precision = 14, scale = 2) private BigDecimal correctedDeductionTotal;
  @Column(name = "corrected_gross_pay", nullable = false, precision = 14, scale = 2) private BigDecimal correctedGrossPay;
  @Column(name = "corrected_tax_rate_percent", nullable = false, precision = 5, scale = 2) private BigDecimal correctedTaxRatePercent;
  @Column(name = "corrected_tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal correctedTaxAmount;
  @Column(name = "corrected_net_pay", nullable = false, precision = 14, scale = 2) private BigDecimal correctedNetPay;
  @Column(name = "corrected_component_details", nullable = false, columnDefinition = "TEXT") private String correctedComponentDetails;
  @Column(nullable = false, length = 1000) private String reason;
  @Column(name = "corrected_by", nullable = false, length = 320) private String correctedBy;
  @Column(name = "corrected_at", nullable = false, updatable = false) private Instant correctedAt = Instant.now();

  protected PayslipCorrection() {}

  PayslipCorrection(Payslip payslip, PayrollCalculation.Result result, String details, String reason, String actor) {
    this.payslip = payslip;
    previousBaseSalary = payslip.getBaseSalary();
    previousAllowanceTotal = payslip.getAllowanceTotal();
    previousDeductionTotal = payslip.getDeductionTotal();
    previousGrossPay = payslip.getGrossPay();
    previousTaxRatePercent = payslip.getTaxRatePercent();
    previousTaxAmount = payslip.getTaxAmount();
    previousNetPay = payslip.getNetPay();
    previousComponentDetails = payslip.getComponentDetails();
    correctedBaseSalary = result.baseSalary();
    correctedAllowanceTotal = result.allowanceTotal();
    correctedDeductionTotal = result.deductionTotal();
    correctedGrossPay = result.grossPay();
    correctedTaxRatePercent = result.taxRatePercent();
    correctedTaxAmount = result.taxAmount();
    correctedNetPay = result.netPay();
    correctedComponentDetails = details;
    this.reason = reason;
    correctedBy = actor;
  }

  public UUID getId() { return id; }
  public BigDecimal getPreviousNetPay() { return previousNetPay; }
  public BigDecimal getCorrectedNetPay() { return correctedNetPay; }
  public String getReason() { return reason; }
  public String getCorrectedBy() { return correctedBy; }
  public Instant getCorrectedAt() { return correctedAt; }
  public String getPreviousComponentDetails() { return previousComponentDetails; }
  public String getCorrectedComponentDetails() { return correctedComponentDetails; }
}

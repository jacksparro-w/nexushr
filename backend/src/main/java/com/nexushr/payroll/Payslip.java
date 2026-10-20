package com.nexushr.payroll;

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
@Table(name = "payslips", uniqueConstraints =
    @UniqueConstraint(name = "uq_payslip_employee_month", columnNames = {"employee_id", "payroll_run_id"}))
public class Payslip {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payroll_run_id", nullable = false) private PayrollRun payrollRun;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false) private Employee employee;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "base_salary", nullable = false, precision = 14, scale = 2) private BigDecimal baseSalary;
  @Column(name = "allowance_total", nullable = false, precision = 14, scale = 2) private BigDecimal allowanceTotal;
  @Column(name = "deduction_total", nullable = false, precision = 14, scale = 2) private BigDecimal deductionTotal;
  @Column(name = "gross_pay", nullable = false, precision = 14, scale = 2) private BigDecimal grossPay;
  @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2) private BigDecimal taxRatePercent;
  @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount;
  @Column(name = "net_pay", nullable = false, precision = 14, scale = 2) private BigDecimal netPay;
  @Column(name = "component_details", nullable = false, columnDefinition = "TEXT") private String componentDetails;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected Payslip() {}

  Payslip(PayrollRun run, Employee employee, String currency, PayrollCalculation.Result result, String details) {
    this.payrollRun = run;
    this.employee = employee;
    this.currency = currency;
    apply(result, details);
  }

  void correct(PayrollCalculation.Result result, String details) { apply(result, details); }

  private void apply(PayrollCalculation.Result result, String details) {
    baseSalary = result.baseSalary();
    allowanceTotal = result.allowanceTotal();
    deductionTotal = result.deductionTotal();
    grossPay = result.grossPay();
    taxRatePercent = result.taxRatePercent();
    taxAmount = result.taxAmount();
    netPay = result.netPay();
    componentDetails = details;
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public PayrollRun getPayrollRun() { return payrollRun; }
  public Employee getEmployee() { return employee; }
  public String getCurrency() { return currency; }
  public BigDecimal getBaseSalary() { return baseSalary; }
  public BigDecimal getAllowanceTotal() { return allowanceTotal; }
  public BigDecimal getDeductionTotal() { return deductionTotal; }
  public BigDecimal getGrossPay() { return grossPay; }
  public BigDecimal getTaxRatePercent() { return taxRatePercent; }
  public BigDecimal getTaxAmount() { return taxAmount; }
  public BigDecimal getNetPay() { return netPay; }
  public String getComponentDetails() { return componentDetails; }
}

package com.nexushr.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class PayrollCalculation {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private PayrollCalculation() {}

  public record Result(BigDecimal baseSalary, BigDecimal allowanceTotal, BigDecimal deductionTotal,
      BigDecimal grossPay, BigDecimal taxRatePercent, BigDecimal taxAmount, BigDecimal netPay) {}

  public static Result calculate(BigDecimal baseSalary, List<PayrollDtos.ComponentSnapshot> components,
      BigDecimal taxRatePercent) {
    requireNonNegative(baseSalary, "Base salary");
    requireNonNegative(taxRatePercent, "Tax rate");
    if (taxRatePercent.compareTo(ONE_HUNDRED) > 0) throw new IllegalArgumentException("Tax rate cannot exceed 100 percent");
    BigDecimal allowances = components.stream().filter(c -> c.type() == PayrollComponentType.ALLOWANCE)
        .map(PayrollDtos.ComponentSnapshot::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal deductions = components.stream().filter(c -> c.type() == PayrollComponentType.DEDUCTION)
        .map(PayrollDtos.ComponentSnapshot::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    requireNonNegative(allowances, "Allowance total");
    requireNonNegative(deductions, "Deduction total");
    BigDecimal gross = money(baseSalary.add(allowances));
    BigDecimal tax = money(gross.multiply(taxRatePercent).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
    BigDecimal net = money(gross.subtract(deductions).subtract(tax));
    if (net.signum() < 0) throw new IllegalArgumentException("Deductions and tax cannot exceed gross pay");
    return new Result(money(baseSalary), money(allowances), money(deductions), gross,
        taxRatePercent.setScale(2, RoundingMode.HALF_UP), tax, net);
  }

  static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

  private static void requireNonNegative(BigDecimal value, String label) {
    if (value == null || value.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative");
  }
}

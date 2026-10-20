package com.nexushr.payroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollCalculationTest {
  @Test
  void appliesConfiguredFlatTaxAndRoundsHalfUpToCurrencyMinorUnits() {
    var result = PayrollCalculation.calculate(new BigDecimal("100.05"), List.of(), new BigDecimal("5.00"));
    assertEquals(new BigDecimal("100.05"), result.grossPay());
    assertEquals(new BigDecimal("5.00"), result.taxAmount());
    assertEquals(new BigDecimal("95.05"), result.netPay());
  }

  @Test
  void calculatesAllowancesAndDeductionsAndRejectsNegativeNetOrTaxRates() {
    var components = List.of(
        new PayrollDtos.ComponentSnapshot("Housing", PayrollComponentType.ALLOWANCE, new BigDecimal("500.00")),
        new PayrollDtos.ComponentSnapshot("Insurance", PayrollComponentType.DEDUCTION, new BigDecimal("100.00")));
    var result = PayrollCalculation.calculate(new BigDecimal("1000.00"), components, new BigDecimal("10.00"));
    assertEquals(new BigDecimal("1500.00"), result.grossPay());
    assertEquals(new BigDecimal("150.00"), result.taxAmount());
    assertEquals(new BigDecimal("1250.00"), result.netPay());
    assertThrows(IllegalArgumentException.class,
        () -> PayrollCalculation.calculate(new BigDecimal("100.00"),
            List.of(new PayrollDtos.ComponentSnapshot("Excess", PayrollComponentType.DEDUCTION, new BigDecimal("200.00"))),
            BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class,
        () -> PayrollCalculation.calculate(BigDecimal.ONE, List.of(), new BigDecimal("100.01")));
  }
}

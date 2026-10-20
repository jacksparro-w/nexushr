package com.nexushr.payroll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class PayrollDtos {
  private PayrollDtos() {}

  public record ComponentRequest(@NotBlank @Size(max = 100) String name,
      @NotNull PayrollComponentType type, @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 2) BigDecimal amount) {}

  public record ComponentSnapshot(String name, PayrollComponentType type, BigDecimal amount) {
    static ComponentSnapshot from(PayrollComponent component) {
      return new ComponentSnapshot(component.getName(), component.getType(), component.getAmount());
    }
  }

  public record StructureRequest(@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
      @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 2) BigDecimal baseSalary,
      @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal taxRatePercent,
      @NotNull LocalDate effectiveFrom, Boolean active,
      @NotNull @Size(max = 30) List<@Valid ComponentRequest> components) {}

  public record StructureResponse(UUID id, UUID employeeId, String employeeName, String employeeCode,
      String currency, BigDecimal baseSalary, BigDecimal taxRatePercent, LocalDate effectiveFrom,
      boolean active, List<ComponentSnapshot> components) {
    static StructureResponse from(PayrollStructure structure) {
      return new StructureResponse(structure.getId(), structure.getEmployee().getId(),
          structure.getEmployee().getFirstName() + " " + structure.getEmployee().getLastName(),
          structure.getEmployee().getEmployeeCode(), structure.getCurrency(), structure.getBaseSalary(),
          structure.getTaxRatePercent(), structure.getEffectiveFrom(), structure.isActive(),
          structure.getComponents().stream().map(ComponentSnapshot::from).toList());
    }
  }

  public record RunResponse(UUID id, YearMonth month, PayrollRunStatus status, String generatedBy,
      Instant generatedAt, String approvedBy, Instant approvedAt, String paidBy, Instant paidAt,
      long payslipCount) {
    static RunResponse from(PayrollRun run, long payslipCount) {
      return new RunResponse(run.getId(), run.getMonth(), run.getStatus(), run.getGeneratedBy(),
          run.getGeneratedAt(), run.getApprovedBy(), run.getApprovedAt(), run.getPaidBy(), run.getPaidAt(), payslipCount);
    }
  }

  public record PayslipResponse(UUID id, UUID runId, YearMonth month, UUID employeeId,
      String employeeName, String employeeCode, String currency, BigDecimal baseSalary,
      BigDecimal allowanceTotal, BigDecimal deductionTotal, BigDecimal grossPay,
      BigDecimal taxRatePercent, BigDecimal taxAmount, BigDecimal netPay,
      PayrollRunStatus runStatus, List<ComponentSnapshot> components) {}

  public record CorrectionRequest(@NotEmpty @Size(max = 30) List<@Valid ComponentRequest> adjustments,
      @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal taxRatePercent,
      @NotBlank @Size(max = 1000) String reason) {}

  public record CorrectionResponse(UUID id, BigDecimal previousNetPay, BigDecimal correctedNetPay,
      String previousComponentDetails, String correctedComponentDetails, String reason,
      String correctedBy, Instant correctedAt) {
    static CorrectionResponse from(PayslipCorrection correction) {
      return new CorrectionResponse(correction.getId(), correction.getPreviousNetPay(), correction.getCorrectedNetPay(),
          correction.getPreviousComponentDetails(), correction.getCorrectedComponentDetails(), correction.getReason(),
          correction.getCorrectedBy(), correction.getCorrectedAt());
    }
  }

  public record PageResponse<T>(List<T> content, int number, int size, long totalElements, int totalPages) {}
}

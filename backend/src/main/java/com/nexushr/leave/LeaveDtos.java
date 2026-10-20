package com.nexushr.leave;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveDtos {
  private LeaveDtos() {}

  public record LeaveTypeRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
      @NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
      boolean paid, Boolean active) {}

  public record LeaveTypeResponse(UUID id, String code, String name, String description, boolean paid, boolean active) {
    static LeaveTypeResponse from(LeaveType type) {
      return new LeaveTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(), type.isPaid(), type.isActive());
    }
  }

  public record BalanceRequest(@NotNull @DecimalMin("0.0") BigDecimal entitledDays) {}

  public record BalanceResponse(UUID employeeId, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName,
      int year, BigDecimal entitledDays, BigDecimal usedDays, BigDecimal pendingDays, BigDecimal availableDays) {}

  public record ApplyRequest(@NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
      @NotBlank @Size(max = 1000) String reason) {}

  public record DecisionRequest(@NotNull LeaveStatus status, @Size(max = 1000) String note) {}

  public record LeaveResponse(UUID id, UUID employeeId, String employeeName, String employeeCode,
      UUID leaveTypeId, String leaveTypeCode, String leaveTypeName, LocalDate startDate, LocalDate endDate,
      BigDecimal days, String reason, LeaveStatus status, String decisionNote, String reviewedBy,
      Instant reviewedAt, Instant createdAt) {
    static LeaveResponse from(LeaveRequest request) {
      return new LeaveResponse(request.getId(), request.getEmployee().getId(),
          request.getEmployee().getFirstName() + " " + request.getEmployee().getLastName(), request.getEmployee().getEmployeeCode(),
          request.getLeaveType().getId(), request.getLeaveType().getCode(), request.getLeaveType().getName(),
          request.getStartDate(), request.getEndDate(), request.getDays(), request.getReason(), request.getStatus(),
          request.getDecisionNote(), request.getReviewedBy(), request.getReviewedAt(), request.getCreatedAt());
    }
  }
}

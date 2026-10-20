package com.nexushr.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.List;

public final class AttendanceDtos {
  private AttendanceDtos() {}

  public record CorrectionRequest(
      @NotNull Instant clockInAt,
      Instant clockOutAt,
      @NotBlank @Size(max = 500) String reason) {}

  public record RecordResponse(
      UUID id,
      UUID employeeId,
      String employeeName,
      String employeeCode,
      LocalDate workDate,
      Instant clockInAt,
      Instant clockOutAt,
      boolean late,
      long workedMinutes,
      String status) {
    static RecordResponse from(AttendanceRecord record) {
      long minutes = record.getClockOutAt() == null
          ? 0
          : Math.max(0, java.time.Duration.between(record.getClockInAt(), record.getClockOutAt()).toMinutes());
      String status = record.getClockOutAt() == null ? "IN_PROGRESS" : "COMPLETE";
      return new RecordResponse(
          record.getId(), record.getEmployee().getId(),
          record.getEmployee().getFirstName() + " " + record.getEmployee().getLastName(),
          record.getEmployee().getEmployeeCode(), record.getWorkDate(), record.getClockInAt(),
          record.getClockOutAt(), record.isLate(), minutes, status);
    }
  }

  public record SummaryResponse(
      YearMonth month, long daysWorked, long lateDays, long totalWorkedMinutes, long completedDays) {}

  public record PageResponse(
      List<RecordResponse> content, int number, int size, long totalElements, int totalPages) {}

  public record AuditResponse(
      UUID id,
      Instant previousClockInAt,
      Instant previousClockOutAt,
      Instant correctedClockInAt,
      Instant correctedClockOutAt,
      String reason,
      String correctedBy,
      Instant correctedAt) {
    static AuditResponse from(AttendanceAudit audit) {
      return new AuditResponse(audit.getId(), audit.getPreviousClockInAt(), audit.getPreviousClockOutAt(),
          audit.getCorrectedClockInAt(), audit.getCorrectedClockOutAt(), audit.getReason(),
          audit.getCorrectedBy(), audit.getCorrectedAt());
    }
  }
}

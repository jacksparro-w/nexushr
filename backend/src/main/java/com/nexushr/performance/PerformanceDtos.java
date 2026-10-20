package com.nexushr.performance;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PerformanceDtos {
  private PerformanceDtos() {}
  public record GoalRequest(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=2000) String description,
      @NotBlank @Size(max=500) String keyResult,@NotNull @FutureOrPresent LocalDate targetDate,GoalStatus status) {}
  public record GoalResponse(UUID id,UUID employeeId,String employeeName,String employeeCode,String title,
      String description,String keyResult,LocalDate targetDate,GoalStatus status) {
    static GoalResponse from(PerformanceGoal g){return new GoalResponse(g.getId(),g.getEmployee().getId(),g.getEmployee().getFirstName()+" "+g.getEmployee().getLastName(),g.getEmployee().getEmployeeCode(),g.getTitle(),g.getDescription(),g.getKeyResult(),g.getTargetDate(),g.getStatus());}
  }
  public record CycleRequest(@NotBlank @Size(max=160) String name,@NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
  public record CycleResponse(UUID id,String name,LocalDate startDate,LocalDate endDate,ReviewCycleStatus status,String createdBy,long reviewCount) {}
  public record SelfAssessmentRequest(@NotBlank @Size(max=3000) String assessment) {}
  public record ManagerReviewRequest(@NotNull @Min(1) @Max(5) Integer rating,@NotBlank @Size(max=3000) String feedback) {}
  public record ReviewResponse(UUID id,UUID cycleId,String cycleName,LocalDate cycleStart,LocalDate cycleEnd,
      ReviewCycleStatus cycleStatus,UUID employeeId,String employeeName,String employeeCode,UUID managerId,
      String selfAssessment,Instant selfSubmittedAt,Integer managerRating,String managerFeedback,Instant completedAt) {
    static ReviewResponse from(PerformanceReview r){return new ReviewResponse(r.getId(),r.getCycle().getId(),r.getCycle().getName(),r.getCycle().getStartDate(),r.getCycle().getEndDate(),r.getCycle().getStatus(),r.getEmployee().getId(),r.getEmployee().getFirstName()+" "+r.getEmployee().getLastName(),r.getEmployee().getEmployeeCode(),r.getManager()==null?null:r.getManager().getId(),r.getSelfAssessment(),r.getSelfSubmittedAt(),r.getManagerRating(),r.getManagerFeedback(),r.getCompletedAt());}
  }
}

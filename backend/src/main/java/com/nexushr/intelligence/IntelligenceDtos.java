package com.nexushr.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class IntelligenceDtos {
  private IntelligenceDtos() {}
  public record WorkforceSummary(long activeEmployees, long goalsInProgress, long goalsCompleted,
      long attendanceRecordsLast30Days, long lateRecordsLast30Days, String dataScope,
      Instant generatedAt) {}
  public record AttritionEstimate(UUID employeeId, String riskBand, int score,
      List<String> contributingFactors, String disclaimer, String modelVersion) {}
  public record QuestionRequest(String question) {}
  public record SkillGapRequest(@NotEmpty @Size(max = 100) List<@Size(max = 80) String> requiredSkills,
      @NotEmpty @Size(max = 500) List<@Size(max = 80) String> currentSkills) {}
  public record SkillGapResponse(int coveragePercent, List<String> matchedSkills,
      List<String> skillGaps, String dataScope, String disclaimer) {}
  public record InsightResponse(String answer, boolean aiGenerated, String dataScope,
      String disclaimer, Instant generatedAt) {}
}

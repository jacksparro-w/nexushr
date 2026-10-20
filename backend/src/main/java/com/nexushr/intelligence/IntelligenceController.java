package com.nexushr.intelligence;

import com.nexushr.intelligence.IntelligenceDtos.InsightResponse;
import com.nexushr.intelligence.IntelligenceDtos.QuestionRequest;
import com.nexushr.intelligence.IntelligenceDtos.SkillGapRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intelligence")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class IntelligenceController {
  private final IntelligenceService service;
  public IntelligenceController(IntelligenceService service) { this.service = service; }

  @GetMapping("/workforce")
  public IntelligenceDtos.WorkforceSummary workforce() { return service.summary(); }

  @GetMapping("/attrition/{employeeId}")
  public IntelligenceDtos.AttritionEstimate attrition(@PathVariable UUID employeeId) {
    return service.attrition(employeeId);
  }

  @PostMapping("/questions")
  public InsightResponse ask(@Valid @RequestBody QuestionRequest request) {
    if (request.question() == null || request.question().isBlank() || request.question().length() > 500) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Question must be 1–500 characters");
    }
    return service.ask(request.question());
  }

  @PostMapping("/skill-gaps")
  public IntelligenceDtos.SkillGapResponse skillGaps(@Valid @RequestBody SkillGapRequest request) {
    return service.skillGaps(request);
  }
}

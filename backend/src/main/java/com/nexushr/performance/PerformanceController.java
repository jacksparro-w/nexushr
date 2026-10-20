package com.nexushr.performance;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/performance") @SecurityRequirement(name="bearerAuth")
public class PerformanceController {
  private final PerformanceService service;
  public PerformanceController(PerformanceService service){this.service=service;}
  @PostMapping("/goals") @ResponseStatus(HttpStatus.CREATED) public PerformanceDtos.GoalResponse createOwnGoal(Authentication auth,@Valid @RequestBody PerformanceDtos.GoalRequest request){return service.createOwnGoal(auth.getName(),request);}
  @GetMapping("/goals/me") public List<PerformanceDtos.GoalResponse> ownGoals(Authentication auth){return service.ownGoals(auth.getName());}
  @PutMapping("/goals/{id}") public PerformanceDtos.GoalResponse updateGoal(@PathVariable UUID id,Authentication auth,@Valid @RequestBody PerformanceDtos.GoalRequest request){return service.updateGoal(auth.getName(),id,request,auth);}
  @PostMapping("/employees/{employeeId}/goals") @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')") @ResponseStatus(HttpStatus.CREATED) public PerformanceDtos.GoalResponse createGoalFor(@PathVariable UUID employeeId,@Valid @RequestBody PerformanceDtos.GoalRequest request,Authentication auth){return service.createGoalFor(employeeId,request,auth);}
  @GetMapping("/employees/{employeeId}/goals") public List<PerformanceDtos.GoalResponse> employeeGoals(@PathVariable UUID employeeId,Authentication auth){return service.employeeGoals(employeeId,auth);}
  @PostMapping("/cycles") @PreAuthorize("hasAnyRole('ADMIN','HR')") @ResponseStatus(HttpStatus.CREATED) public PerformanceDtos.CycleResponse createCycle(@Valid @RequestBody PerformanceDtos.CycleRequest request,Authentication auth){return service.createCycle(request,auth.getName());}
  @GetMapping("/cycles") @PreAuthorize("hasAnyRole('ADMIN','HR')") public List<PerformanceDtos.CycleResponse> cycles(){return service.cycles();}
  @PostMapping("/cycles/{id}/close") @PreAuthorize("hasAnyRole('ADMIN','HR')") public PerformanceDtos.CycleResponse closeCycle(@PathVariable UUID id){return service.closeCycle(id);}
  @GetMapping("/reviews/me") public List<PerformanceDtos.ReviewResponse> ownReviews(Authentication auth){return service.ownReviews(auth.getName());}
  @GetMapping("/reviews/team") @PreAuthorize("hasRole('MANAGER')") public List<PerformanceDtos.ReviewResponse> teamReviews(Authentication auth){return service.teamReviews(auth);}
  @GetMapping("/reviews") @PreAuthorize("hasAnyRole('ADMIN','HR')") public List<PerformanceDtos.ReviewResponse> allReviews(){return service.allReviews();}
  @GetMapping("/reviews/{id}") public PerformanceDtos.ReviewResponse review(@PathVariable UUID id,Authentication auth){return service.review(id,auth);}
  @PostMapping("/reviews/{id}/self-assessment") public PerformanceDtos.ReviewResponse selfAssess(@PathVariable UUID id,Authentication auth,@Valid @RequestBody PerformanceDtos.SelfAssessmentRequest request){return service.selfAssess(id,auth.getName(),request);}
  @PostMapping("/reviews/{id}/manager-review") @PreAuthorize("hasRole('MANAGER')") public PerformanceDtos.ReviewResponse managerReview(@PathVariable UUID id,Authentication auth,@Valid @RequestBody PerformanceDtos.ManagerReviewRequest request){return service.managerReview(id,auth,request);}
}

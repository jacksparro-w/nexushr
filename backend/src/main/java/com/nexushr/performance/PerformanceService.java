package com.nexushr.performance;

import com.nexushr.auth.UserAccount;
import com.nexushr.auth.UserRepository;
import com.nexushr.employee.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerformanceService {
  private final PerformanceGoalRepository goals; private final PerformanceReviewCycleRepository cycles;
  private final PerformanceReviewRepository reviews; private final EmployeeRepository employees; private final UserRepository users;
  public PerformanceService(PerformanceGoalRepository goals,PerformanceReviewCycleRepository cycles,PerformanceReviewRepository reviews,EmployeeRepository employees,UserRepository users){this.goals=goals;this.cycles=cycles;this.reviews=reviews;this.employees=employees;this.users=users;}

  @Transactional public PerformanceDtos.GoalResponse createOwnGoal(String principal,PerformanceDtos.GoalRequest request){return createGoal(resolveEmployee(principal),request);}
  @Transactional public PerformanceDtos.GoalResponse createGoalFor(UUID employeeId,PerformanceDtos.GoalRequest request,Authentication auth){Employee target=employee(employeeId);authorizeEmployee(auth,target);return createGoal(target,request);}
  private PerformanceDtos.GoalResponse createGoal(Employee e,PerformanceDtos.GoalRequest r){if(e.getStatus()!=EmploymentStatus.ACTIVE)bad("Goals can only be created for active employees");return PerformanceDtos.GoalResponse.from(goals.save(new PerformanceGoal(e,r)));}
  @Transactional public PerformanceDtos.GoalResponse updateGoal(String principal,UUID id,PerformanceDtos.GoalRequest request,Authentication auth){PerformanceGoal goal=goals.findById(id).orElseThrow(()->notFound("Goal not found"));authorizeEmployee(auth,goal.getEmployee());if(goal.getStatus()!=GoalStatus.IN_PROGRESS)conflict("Only in-progress goals can be changed");goal.update(request);return PerformanceDtos.GoalResponse.from(goal);}
  @Transactional(readOnly=true) public List<PerformanceDtos.GoalResponse> ownGoals(String principal){return goalResponses(goals.findByEmployeeIdOrderByTargetDateAsc(resolveEmployee(principal).getId()));}
  @Transactional(readOnly=true) public List<PerformanceDtos.GoalResponse> employeeGoals(UUID id,Authentication auth){Employee e=employee(id);authorizeEmployee(auth,e);return goalResponses(goals.findByEmployeeIdOrderByTargetDateAsc(id));}
  private List<PerformanceDtos.GoalResponse> goalResponses(List<PerformanceGoal> values){return values.stream().map(PerformanceDtos.GoalResponse::from).toList();}

  @Transactional public PerformanceDtos.CycleResponse createCycle(PerformanceDtos.CycleRequest r,String actor){if(r.endDate().isBefore(r.startDate()))bad("Review cycle end date must be on or after its start date");PerformanceReviewCycle cycle=cycles.save(new PerformanceReviewCycle(r,resolveEmail(actor)));List<Employee> active=employees.findAll().stream().filter(e->e.getStatus()==EmploymentStatus.ACTIVE).toList();for(Employee e:active)reviews.save(new PerformanceReview(cycle,e));return cycleResponse(cycle);}
  @Transactional(readOnly=true) public List<PerformanceDtos.CycleResponse> cycles(){return cycles.findAllByOrderByStartDateDesc().stream().map(this::cycleResponse).toList();}
  @Transactional public PerformanceDtos.CycleResponse closeCycle(UUID id){PerformanceReviewCycle c=cycle(id);c.close();return cycleResponse(c);}
  @Transactional(readOnly=true) public List<PerformanceDtos.ReviewResponse> ownReviews(String principal){Employee e=resolveEmployee(principal);return reviewResponses(reviews.findByEmployeeIdOrderByCreatedAtDesc(e.getId()));}
  @Transactional(readOnly=true) public List<PerformanceDtos.ReviewResponse> teamReviews(Authentication auth){Employee manager=resolveEmployee(auth.getName());return reviewResponses(reviews.findByEmployeeManagerIdOrderByCreatedAtDesc(manager.getId()));}
  @Transactional(readOnly=true) public List<PerformanceDtos.ReviewResponse> allReviews(){return reviewResponses(reviews.findAllByOrderByCreatedAtDesc());}
  @Transactional(readOnly=true) public PerformanceDtos.ReviewResponse review(UUID id,Authentication auth){PerformanceReview r=reviewEntity(id);authorizeEmployee(auth,r.getEmployee());return PerformanceDtos.ReviewResponse.from(r);}
  @Transactional public PerformanceDtos.ReviewResponse selfAssess(UUID id,String principal,PerformanceDtos.SelfAssessmentRequest request){PerformanceReview r=reviewEntity(id);Employee actor=resolveEmployee(principal);if(!actor.getId().equals(r.getEmployee().getId()))forbidden("You can only submit your own self-assessment");requireOpenAndInWindow(r.getCycle());if(r.getSelfSubmittedAt()!=null)conflict("Self-assessment has already been submitted");r.submitSelf(request.assessment());return PerformanceDtos.ReviewResponse.from(r);}
  @Transactional public PerformanceDtos.ReviewResponse managerReview(UUID id,Authentication auth,PerformanceDtos.ManagerReviewRequest request){PerformanceReview r=reviewEntity(id);Employee manager=resolveEmployee(auth.getName());if(r.getEmployee().getManager()==null||!r.getEmployee().getManager().getId().equals(manager.getId()))forbidden("Only the employee's direct manager may complete this review");requireOpenAndInWindow(r.getCycle());if(r.getSelfSubmittedAt()==null)conflict("Employee must submit the self-assessment before manager feedback");if(r.getCompletedAt()!=null)conflict("This review has already been completed");r.complete(manager,request.rating(),request.feedback());return PerformanceDtos.ReviewResponse.from(r);}
  private void requireOpenAndInWindow(PerformanceReviewCycle c){LocalDate today=LocalDate.now();if(c.getStatus()!=ReviewCycleStatus.OPEN)conflict("Review cycle is closed");if(today.isBefore(c.getStartDate())||today.isAfter(c.getEndDate()))conflict("This review is outside the cycle deadline");}
  private void authorizeEmployee(Authentication auth,Employee target){if(has(auth,"ADMIN")||has(auth,"HR"))return;Employee actor=resolveEmployee(auth.getName());if(actor.getId().equals(target.getId()))return;if(has(auth,"MANAGER")&&target.getManager()!=null&&target.getManager().getId().equals(actor.getId()))return;forbidden("You are not allowed to access this employee's performance data");}
  private boolean has(Authentication a,String role){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));}
  private PerformanceDtos.CycleResponse cycleResponse(PerformanceReviewCycle c){return new PerformanceDtos.CycleResponse(c.getId(),c.getName(),c.getStartDate(),c.getEndDate(),c.getStatus(),c.getCreatedBy(),reviews.countByCycleId(c.getId()));}
  private List<PerformanceDtos.ReviewResponse> reviewResponses(List<PerformanceReview> values){return values.stream().map(PerformanceDtos.ReviewResponse::from).toList();}
  private PerformanceReview reviewEntity(UUID id){return reviews.findById(id).orElseThrow(()->notFound("Review not found"));}
  private PerformanceReviewCycle cycle(UUID id){return cycles.findById(id).orElseThrow(()->notFound("Review cycle not found"));}
  private Employee employee(UUID id){return employees.findById(id).orElseThrow(()->notFound("Employee not found"));}
  private Employee resolveEmployee(String principal){return employees.findByWorkEmailIgnoreCase(resolveEmail(principal)).orElseThrow(()->notFound("Employee profile not found"));}
  private String resolveEmail(String principal){try{return users.findById(UUID.fromString(principal)).map(UserAccount::getEmail).orElse(principal);}catch(IllegalArgumentException e){return principal;}}
  private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private static void forbidden(String m){throw new ResponseStatusException(HttpStatus.FORBIDDEN,m);}private static void conflict(String m){throw new ResponseStatusException(HttpStatus.CONFLICT,m);}
}

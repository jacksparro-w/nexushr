package com.nexushr.leave;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaves")
@SecurityRequirement(name = "bearerAuth")
public class LeaveController {
  private final LeaveService service;
  public LeaveController(LeaveService service) { this.service = service; }

  @GetMapping("/types")
  public List<LeaveDtos.LeaveTypeResponse> types(@RequestParam(defaultValue = "false") boolean includeInactive) {
    return service.leaveTypes(includeInactive);
  }

  @PostMapping("/types")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveDtos.LeaveTypeResponse createType(@Valid @RequestBody LeaveDtos.LeaveTypeRequest request) {
    return service.createType(request);
  }

  @PutMapping("/types/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public LeaveDtos.LeaveTypeResponse updateType(@PathVariable UUID id, @Valid @RequestBody LeaveDtos.LeaveTypeRequest request) {
    return service.updateType(id, request);
  }

  @GetMapping("/me/balances")
  public List<LeaveDtos.BalanceResponse> ownBalances(Authentication auth,
      @RequestParam(required = false) Integer year) {
    return service.ownBalances(auth.getName(), year == null ? java.time.Year.now().getValue() : year);
  }

  @GetMapping("/employees/{employeeId}/balances")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public List<LeaveDtos.BalanceResponse> employeeBalances(@PathVariable UUID employeeId,
      @RequestParam(required = false) Integer year) {
    return service.employeeBalances(employeeId, year == null ? java.time.Year.now().getValue() : year);
  }

  @PutMapping("/employees/{employeeId}/balances/{leaveTypeId}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public LeaveDtos.BalanceResponse setBalance(@PathVariable UUID employeeId, @PathVariable UUID leaveTypeId,
      @RequestParam int year, @Valid @RequestBody LeaveDtos.BalanceRequest request) {
    return service.setBalance(employeeId, leaveTypeId, year, request);
  }

  @PostMapping("/me/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveDtos.LeaveResponse apply(Authentication auth, @Valid @RequestBody LeaveDtos.ApplyRequest request) {
    return service.apply(auth.getName(), request);
  }

  @GetMapping("/me/requests")
  public List<LeaveDtos.LeaveResponse> ownRequests(Authentication auth) { return service.ownRequests(auth.getName()); }

  @PostMapping("/me/requests/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelOwn(Authentication auth, @PathVariable UUID id) { service.cancelOwn(auth.getName(), id); }

  @GetMapping("/requests")
  @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
  public List<LeaveDtos.LeaveResponse> managedRequests(Authentication auth,
      @RequestParam(required = false) LeaveStatus status) {
    return service.managedRequests(auth, status);
  }

  @PostMapping("/requests/{id}/decision")
  @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
  public LeaveDtos.LeaveResponse decide(@PathVariable UUID id, @Valid @RequestBody LeaveDtos.DecisionRequest request,
      Authentication auth) {
    return service.decide(id, request, auth);
  }
}

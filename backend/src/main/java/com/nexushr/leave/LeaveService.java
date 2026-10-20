package com.nexushr.leave;

import com.nexushr.auth.UserAccount;
import com.nexushr.auth.UserRepository;
import com.nexushr.employee.Employee;
import com.nexushr.employee.EmployeeRepository;
import com.nexushr.employee.EmploymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaveService {
  private static final List<LeaveStatus> BALANCE_RESERVING = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
  private final LeaveTypeRepository types;
  private final LeaveBalanceRepository balances;
  private final LeaveRequestRepository requests;
  private final EmployeeRepository employees;
  private final UserRepository users;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher events;
  private final ZoneId zone;

  public LeaveService(LeaveTypeRepository types, LeaveBalanceRepository balances, LeaveRequestRepository requests,
      EmployeeRepository employees, UserRepository users, EntityManager entityManager,
      @Value("${app.attendance.time-zone:Asia/Kolkata}") String timeZone,
      ApplicationEventPublisher events) {
    this.types = types;
    this.balances = balances;
    this.requests = requests;
    this.employees = employees;
    this.users = users;
    this.entityManager = entityManager;
    this.events = events;
    this.zone = ZoneId.of(timeZone);
  }

  @Transactional(readOnly = true)
  public List<LeaveDtos.LeaveTypeResponse> leaveTypes(boolean includeInactive) {
    return (includeInactive ? types.findAllByOrderByNameAsc() : types.findByActiveTrueOrderByNameAsc())
        .stream().map(LeaveDtos.LeaveTypeResponse::from).toList();
  }

  @Transactional
  public LeaveDtos.LeaveTypeResponse createType(LeaveDtos.LeaveTypeRequest request) {
    if (types.existsByCodeIgnoreCase(request.code())) conflict("Leave type code already exists");
    return LeaveDtos.LeaveTypeResponse.from(types.save(new LeaveType(request)));
  }

  @Transactional
  public LeaveDtos.LeaveTypeResponse updateType(UUID id, LeaveDtos.LeaveTypeRequest request) {
    LeaveType type = types.findById(id).orElseThrow(() -> notFound("Leave type not found"));
    types.findByCodeIgnoreCase(request.code()).filter(existing -> !existing.getId().equals(id))
        .ifPresent(existing -> conflict("Leave type code already exists"));
    type.update(request);
    return LeaveDtos.LeaveTypeResponse.from(type);
  }

  @Transactional(readOnly = true)
  public List<LeaveDtos.BalanceResponse> ownBalances(String principal, int year) {
    Employee employee = employeeByEmail(resolveEmail(principal));
    return balanceViews(employee, year);
  }

  @Transactional(readOnly = true)
  public List<LeaveDtos.BalanceResponse> employeeBalances(UUID employeeId, int year) {
    return balanceViews(findEmployee(employeeId), year);
  }

  @Transactional
  public LeaveDtos.BalanceResponse setBalance(UUID employeeId, UUID typeId, int year, LeaveDtos.BalanceRequest request) {
    Employee employee = lockEmployee(employeeId);
    LeaveType type = types.findById(typeId).orElseThrow(() -> notFound("Leave type not found"));
    LeaveBalance balance = balances.findForUpdate(employeeId, typeId, year).orElse(null);
    BigDecimal used = totalFor(employeeId, typeId, year, LeaveStatus.APPROVED);
    BigDecimal pending = totalFor(employeeId, typeId, year, LeaveStatus.PENDING);
    if (request.entitledDays().compareTo(used.add(pending)) < 0) {
      conflict("Leave balance cannot be lower than approved and pending leave");
    }
    if (balance == null) balance = new LeaveBalance(employee, type, year, request.entitledDays());
    else balance.setEntitledDays(request.entitledDays());
    return balanceView(balances.save(balance), used, pending);
  }

  @Transactional
  public LeaveDtos.LeaveResponse apply(String principal, LeaveDtos.ApplyRequest request) {
    Employee profile = employeeByEmail(resolveEmail(principal));
    Employee employee = lockEmployee(profile.getId());
    if (employee.getStatus() != EmploymentStatus.ACTIVE) conflict("Only active employees can request leave");
    validatePeriod(request.startDate(), request.endDate());
    LeaveType type = types.findById(request.leaveTypeId()).orElseThrow(() -> notFound("Leave type not found"));
    if (!type.isActive()) conflict("This leave type is inactive");
    BigDecimal days = workingDays(request.startDate(), request.endDate());
    if (days.signum() == 0) badRequest("Leave period must include at least one weekday");
    if (!requests.findOverlapping(employee.getId(), BALANCE_RESERVING, request.startDate(), request.endDate()).isEmpty()) {
      conflict("Leave dates overlap an existing pending or approved request");
    }
    LeaveBalance balance = balances.findForUpdate(employee.getId(), type.getId(), request.startDate().getYear()).orElse(null);
    BigDecimal entitled = balance == null ? BigDecimal.ZERO : balance.getEntitledDays();
    BigDecimal used = totalFor(employee.getId(), type.getId(), request.startDate().getYear(), LeaveStatus.APPROVED);
    BigDecimal pending = totalFor(employee.getId(), type.getId(), request.startDate().getYear(), LeaveStatus.PENDING);
    if (used.add(pending).add(days).compareTo(entitled) > 0) conflict("Insufficient leave balance");
    return LeaveDtos.LeaveResponse.from(requests.save(new LeaveRequest(employee, type, request, days)));
  }

  @Transactional
  public void cancelOwn(String principal, UUID requestId) {
    String email = resolveEmail(principal);
    LeaveRequest existing = requests.findById(requestId).orElseThrow(() -> notFound("Leave request not found"));
    if (!existing.getEmployee().getWorkEmail().equalsIgnoreCase(email)) forbidden("You can cancel only your own leave request");
    lockEmployee(existing.getEmployee().getId());
    LeaveRequest request = requests.findForUpdate(requestId).orElseThrow(() -> notFound("Leave request not found"));
    if (request.getStatus() != LeaveStatus.PENDING) conflict("Only pending leave requests can be cancelled");
    request.cancel();
  }

  @Transactional
  public LeaveDtos.LeaveResponse decide(UUID requestId, LeaveDtos.DecisionRequest decision, Authentication authentication) {
    if (decision.status() != LeaveStatus.APPROVED && decision.status() != LeaveStatus.REJECTED) {
      badRequest("Decision status must be APPROVED or REJECTED");
    }
    LeaveRequest existing = requests.findById(requestId).orElseThrow(() -> notFound("Leave request not found"));
    boolean manager = hasRole(authentication, "MANAGER");
    String actorEmail = resolveEmail(authentication.getName());
    if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "HR") &&
        !(manager && existing.getEmployee().getManager() != null &&
            existing.getEmployee().getManager().getWorkEmail().equalsIgnoreCase(actorEmail))) {
      forbidden("Only HR, administrators, or the employee's manager can review this request");
    }
    lockEmployee(existing.getEmployee().getId());
    LeaveRequest request = requests.findForUpdate(requestId).orElseThrow(() -> notFound("Leave request not found"));
    if (request.getStatus() != LeaveStatus.PENDING) conflict("Only pending leave requests can be reviewed");
    if (decision.status() == LeaveStatus.APPROVED) {
      int year = request.getStartDate().getYear();
      LeaveBalance balance = balances.findForUpdate(request.getEmployee().getId(), request.getLeaveType().getId(), year).orElse(null);
      BigDecimal entitled = balance == null ? BigDecimal.ZERO : balance.getEntitledDays();
      BigDecimal used = totalFor(request.getEmployee().getId(), request.getLeaveType().getId(), year, LeaveStatus.APPROVED);
      if (used.add(request.getDays()).compareTo(entitled) > 0) conflict("Insufficient leave balance to approve this request");
    }
    request.decide(decision.status(), decision.note(), actorEmail);
    events.publishEvent(new com.nexushr.notification.NotificationEvents.LeaveDecision(
        request.getEmployee().getWorkEmail(), request.getLeaveType().getName(),
        request.getStartDate().toString(), request.getEndDate().toString(),
        decision.status().name(), request.getId().toString()));
    return LeaveDtos.LeaveResponse.from(request);
  }

  @Transactional(readOnly = true)
  public List<LeaveDtos.LeaveResponse> ownRequests(String principal) {
    Employee employee = employeeByEmail(resolveEmail(principal));
    return requests.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream().map(LeaveDtos.LeaveResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<LeaveDtos.LeaveResponse> managedRequests(Authentication authentication, LeaveStatus status) {
    List<LeaveRequest> found;
    if (hasRole(authentication, "ADMIN") || hasRole(authentication, "HR")) {
      found = status == null ? requests.findAllByOrderByCreatedAtDesc() : requests.findByStatusOrderByCreatedAtAsc(status);
    } else if (hasRole(authentication, "MANAGER")) {
      String email = resolveEmail(authentication.getName());
      found = requests.findByEmployeeManagerWorkEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
          .filter(request -> status == null || request.getStatus() == status).toList();
    } else {
      forbidden("Leave approvals are restricted to managers, HR, and administrators");
      return List.of();
    }
    return found.stream().map(LeaveDtos.LeaveResponse::from).toList();
  }

  private List<LeaveDtos.BalanceResponse> balanceViews(Employee employee, int year) {
    return types.findAllByOrderByNameAsc().stream().map(type -> {
      LeaveBalance balance = balances.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), type.getId(), year).orElse(null);
      BigDecimal used = totalFor(employee.getId(), type.getId(), year, LeaveStatus.APPROVED);
      BigDecimal pending = totalFor(employee.getId(), type.getId(), year, LeaveStatus.PENDING);
      if (balance == null && used.signum() == 0 && pending.signum() == 0) {
        return new LeaveDtos.BalanceResponse(employee.getId(), type.getId(), type.getCode(), type.getName(), year,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
      }
      return balanceView(balance, employee, type, year, used, pending);
    }).toList();
  }

  private LeaveDtos.BalanceResponse balanceView(LeaveBalance balance, BigDecimal used, BigDecimal pending) {
    return balanceView(balance, balance.getEmployee(), balance.getLeaveType(), balance.getYear(), used, pending);
  }

  private LeaveDtos.BalanceResponse balanceView(LeaveBalance balance, Employee employee, LeaveType type, int year,
      BigDecimal used, BigDecimal pending) {
    BigDecimal entitled = balance == null ? BigDecimal.ZERO : balance.getEntitledDays();
    return new LeaveDtos.BalanceResponse(employee.getId(), type.getId(), type.getCode(), type.getName(), year,
        entitled, used, pending, entitled.subtract(used).subtract(pending).max(BigDecimal.ZERO));
  }

  private BigDecimal totalFor(UUID employeeId, UUID typeId, int year, LeaveStatus status) {
    return requests.sumDays(employeeId, typeId, year, status);
  }

  private static BigDecimal workingDays(LocalDate from, LocalDate to) {
    long days = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      DayOfWeek day = date.getDayOfWeek();
      if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) days++;
    }
    return BigDecimal.valueOf(days);
  }

  private void validatePeriod(LocalDate from, LocalDate to) {
    if (to.isBefore(from)) badRequest("End date must be on or after start date");
    if (from.getYear() != to.getYear()) badRequest("A leave request must stay within one calendar year");
    if (from.isBefore(LocalDate.now(java.time.Clock.systemUTC().withZone(zone)))) badRequest("Leave requests cannot start in the past");
  }

  private Employee lockEmployee(UUID id) {
    Employee employee = findEmployee(id);
    return entityManager.find(Employee.class, employee.getId(), LockModeType.PESSIMISTIC_WRITE);
  }

  private Employee findEmployee(UUID id) { return employees.findById(id).orElseThrow(() -> notFound("Employee not found")); }
  private Employee employeeByEmail(String email) { return employees.findByWorkEmailIgnoreCase(email).orElseThrow(() -> notFound("Employee profile not found")); }

  private String resolveEmail(String principal) {
    try { return users.findById(UUID.fromString(principal)).map(UserAccount::getEmail).orElse(principal); }
    catch (IllegalArgumentException notUuid) { return principal; }
  }

  private static boolean hasRole(Authentication auth, String role) {
    return auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
  }

  private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
  private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
  private static void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
  private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}

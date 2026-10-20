package com.nexushr.employee;

import com.nexushr.attendance.AttendanceRepository;
import com.nexushr.leave.LeaveBalanceRepository;
import com.nexushr.leave.LeaveRequestRepository;
import com.nexushr.payroll.PayrollStructureRepository;
import com.nexushr.payroll.PayslipRepository;
import com.nexushr.performance.PerformanceGoalRepository;
import com.nexushr.performance.PerformanceReviewRepository;
import com.nexushr.department.Department;
import com.nexushr.department.DepartmentRepository;
import com.nexushr.auth.UserRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
  private static final int MAX_PAGE_SIZE = 100;
  private final EmployeeRepository employees;
  private final DepartmentRepository departments;
  private final EmployeeDocumentRepository documents;
  private final UserRepository users;
  private final AttendanceRepository attendance;
  private final LeaveRequestRepository leaveRequests;
  private final LeaveBalanceRepository leaveBalances;
  private final PayrollStructureRepository payrollStructures;
  private final PayslipRepository payslips;
  private final PerformanceGoalRepository performanceGoals;
  private final PerformanceReviewRepository performanceReviews;

  public EmployeeController(EmployeeRepository employees, DepartmentRepository departments,
      EmployeeDocumentRepository documents, UserRepository users, AttendanceRepository attendance,
      LeaveRequestRepository leaveRequests, LeaveBalanceRepository leaveBalances,
      PayrollStructureRepository payrollStructures, PayslipRepository payslips,
      PerformanceGoalRepository performanceGoals, PerformanceReviewRepository performanceReviews) {
    this.employees = employees;
    this.departments = departments;
    this.documents = documents;
    this.users = users;
    this.attendance = attendance;
    this.leaveRequests = leaveRequests;
    this.leaveBalances = leaveBalances;
    this.payrollStructures = payrollStructures;
    this.payslips = payslips;
    this.performanceGoals = performanceGoals;
    this.performanceReviews = performanceReviews;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Page<EmployeeDtos.Response> list(@RequestParam(required = false) String q,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) EmploymentStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size, Authentication authentication) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
    }
    Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()));
    boolean manager = hasRole(authentication, "MANAGER");
    if (!manager && !hasRole(authentication, "ADMIN") && !hasRole(authentication, "HR")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Directory access is restricted");
    }
    Specification<Employee> spec = (root, query, cb) -> cb.conjunction();
    if (manager) {
      String managerEmail = currentEmail(authentication).toLowerCase();
      spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("manager").get("workEmail")), managerEmail));
    }
    if (q != null && !q.isBlank()) {
      String pattern = "%" + q.trim().toLowerCase() + "%";
      spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("employeeCode")), pattern),
          cb.like(cb.lower(root.get("firstName")), pattern), cb.like(cb.lower(root.get("lastName")), pattern),
          cb.like(cb.lower(root.get("workEmail")), pattern)));
    }
    if (departmentId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId));
    if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    return employees.findAll(spec, pageable).map(EmployeeDtos.Response::from);
  }

  @GetMapping("/me")
  @Transactional(readOnly = true)
  public EmployeeDtos.Response me(Authentication authentication) {
    return EmployeeDtos.Response.from(employees.findByWorkEmailIgnoreCase(currentEmail(authentication))
        .orElseThrow(() -> notFound("Employee profile not found")));
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public EmployeeDtos.Response get(@PathVariable UUID id, Authentication authentication) {
    Employee employee = find(id);
    if (!canView(employee, authentication)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee profile access denied");
    return EmployeeDtos.Response.from(employee);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeeDtos.Response create(@Valid @RequestBody EmployeeDtos.SaveRequest request) {
    if (employees.existsByEmployeeCodeIgnoreCase(request.employeeCode())) conflict("Employee code already exists");
    if (employees.existsByWorkEmailIgnoreCase(request.workEmail())) conflict("Work email already exists");
    Department department = departments.findById(request.departmentId()).orElseThrow(() -> notFound("Department not found"));
    if (!department.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign employee to an inactive department");
    Employee manager = findManager(request.managerId());
    return EmployeeDtos.Response.from(employees.save(new Employee(request, department, manager)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public EmployeeDtos.Response update(@PathVariable UUID id, @Valid @RequestBody EmployeeDtos.SaveRequest request) {
    Employee employee = find(id);
    if (!employee.getEmployeeCode().equalsIgnoreCase(request.employeeCode()) && employees.existsByEmployeeCodeIgnoreCase(request.employeeCode())) conflict("Employee code already exists");
    if (!employee.getWorkEmail().equalsIgnoreCase(request.workEmail()) && employees.existsByWorkEmailIgnoreCase(request.workEmail())) conflict("Work email already exists");
    Department department = departments.findById(request.departmentId()).orElseThrow(() -> notFound("Department not found"));
    if (!department.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign employee to an inactive department");
    if (id.equals(request.managerId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An employee cannot manage themselves");
    employee.update(request, department, findManager(request.managerId()));
    return EmployeeDtos.Response.from(employees.save(employee));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    Employee employee = find(id);
    if (employees.existsByManagerId(id)) conflict("Employee has direct reports and cannot be deleted");
    if (attendance.existsByEmployeeId(id)) conflict("Employee attendance history prevents deletion; deactivate the employee instead");
    if (leaveRequests.existsByEmployeeId(id) || leaveBalances.existsByEmployeeId(id)) conflict("Employee leave history or balance prevents deletion; deactivate the employee instead");
    if (payrollStructures.existsByEmployeeId(id) || payslips.existsByEmployeeId(id)) conflict("Payroll structures or payslip history prevents deletion; deactivate the employee instead");
    if (performanceGoals.existsByEmployeeId(id) || performanceReviews.existsByEmployeeIdOrManagerId(id, id)) conflict("Performance goals or review history prevents deletion; deactivate the employee instead");
    employees.delete(employee);
  }

  @GetMapping("/{id}/documents")
  @Transactional(readOnly = true)
  public java.util.List<EmployeeDtos.DocumentResponse> listDocuments(@PathVariable UUID id, Authentication authentication) {
    Employee employee = find(id);
    if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "HR") &&
        !employee.getWorkEmail().equalsIgnoreCase(currentEmail(authentication))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document metadata access denied");
    }
    return documents.findAllByEmployeeIdOrderByUploadedAtDesc(id).stream().map(EmployeeDtos.DocumentResponse::from).toList();
  }

  @PostMapping("/{id}/documents")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeeDtos.DocumentResponse addDocument(@PathVariable UUID id, @Valid @RequestBody EmployeeDtos.DocumentRequest request) {
    return EmployeeDtos.DocumentResponse.from(documents.save(new EmployeeDocument(find(id), request)));
  }

  @DeleteMapping("/{id}/documents/{documentId}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
    EmployeeDocument document = documents.findByIdAndEmployeeId(documentId, id).orElseThrow(() -> notFound("Document metadata not found"));
    documents.delete(document);
  }

  private Employee find(UUID id) { return employees.findById(id).orElseThrow(() -> notFound("Employee not found")); }
  private Employee findManager(UUID id) {
    if (id == null) return null;
    Employee manager = find(id);
    if (manager.getStatus() != EmploymentStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Manager must be active");
    return manager;
  }
  private boolean canView(Employee employee, Authentication auth) {
    String email = currentEmail(auth);
    if (hasRole(auth, "ADMIN") || hasRole(auth, "HR") || employee.getWorkEmail().equalsIgnoreCase(email)) return true;
    return hasRole(auth, "MANAGER") && employee.getManager() != null && employee.getManager().getWorkEmail().equalsIgnoreCase(email);
  }
  private String currentEmail(Authentication authentication) {
    try {
      return users.findById(UUID.fromString(authentication.getName()))
          .map(com.nexushr.auth.UserAccount::getEmail)
          .orElse(authentication.getName());
    } catch (IllegalArgumentException invalidPrincipal) {
      return authentication.getName();
    }
  }
  private static boolean hasRole(Authentication auth, String role) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)); }
  private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
  private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}

package com.nexushr.department;

import com.nexushr.employee.EmployeeRepository;
import com.nexushr.employee.Employee;
import com.nexushr.employee.EmploymentStatus;
import com.nexushr.auth.Role;
import com.nexushr.auth.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
  private final DepartmentRepository departments;
  private final EmployeeRepository employees;
  private final UserRepository users;

  public DepartmentController(DepartmentRepository departments, EmployeeRepository employees, UserRepository users) {
    this.departments = departments;
    this.employees = employees;
    this.users = users;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<DepartmentDtos.Response> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
    return departments.findAllByOrderByNameAsc().stream()
        .filter(d -> includeInactive || d.isActive())
        .map(DepartmentDtos.Response::from).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public DepartmentDtos.Response create(@Valid @RequestBody DepartmentDtos.SaveRequest request) {
    if (departments.existsByCodeIgnoreCase(request.code())) conflict("Department code already exists");
    if (departments.existsByNameIgnoreCase(request.name())) conflict("Department name already exists");
    return DepartmentDtos.Response.from(departments.save(new Department(request.code(), request.name(),
        request.description(), request.active() == null || request.active(), findManager(request.managerId()))));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @Transactional
  public DepartmentDtos.Response update(@PathVariable UUID id, @Valid @RequestBody DepartmentDtos.SaveRequest request) {
    Department department = departments.findById(id).orElseThrow(() -> notFound("Department not found"));
    if (!department.getCode().equalsIgnoreCase(request.code())) conflict("Department code cannot be changed");
    if (!department.getName().equalsIgnoreCase(request.name()) && departments.existsByNameIgnoreCase(request.name())) conflict("Department name already exists");
    department.update(request.name(), request.description(), request.active() == null || request.active(),
        findManager(request.managerId()));
    return DepartmentDtos.Response.from(departments.save(department));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    Department department = departments.findById(id).orElseThrow(() -> notFound("Department not found"));
    if (employees.existsByDepartmentId(id)) conflict("Department has employees; deactivate it instead");
    departments.delete(department);
  }

  private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
  private Employee findManager(UUID id) {
    if (id == null) return null;
    Employee manager = employees.findById(id).orElseThrow(() -> notFound("Manager employee profile not found"));
    if (manager.getStatus() != EmploymentStatus.ACTIVE) conflict("Manager must be active");
    boolean hasManagerAccount = users.findByEmailIgnoreCase(manager.getWorkEmail())
        .map(account -> account.isEnabled() && account.getRoles().contains(Role.MANAGER))
        .orElse(false);
    if (!hasManagerAccount) conflict("Assign an enabled MANAGER login to this employee before selecting them as a department manager");
    return manager;
  }
  private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}

package com.nexushr.project;

import com.nexushr.auth.Role;
import com.nexushr.auth.UserRepository;
import com.nexushr.department.Department;
import com.nexushr.department.DepartmentRepository;
import com.nexushr.employee.Employee;
import com.nexushr.employee.EmployeeRepository;
import com.nexushr.employee.EmploymentStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectRepository projects;
  private final DepartmentRepository departments;
  private final EmployeeRepository employees;
  private final UserRepository users;

  public ProjectController(ProjectRepository projects, DepartmentRepository departments,
      EmployeeRepository employees, UserRepository users) {
    this.projects = projects;
    this.departments = departments;
    this.employees = employees;
    this.users = users;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<ProjectDtos.Response> list() {
    return projects.findAllByOrderByNameAsc().stream().map(ProjectDtos.Response::from).toList();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public ProjectDtos.Response create(@Valid @RequestBody ProjectDtos.SaveRequest request) {
    if (projects.existsByCodeIgnoreCase(request.code())) conflict("Project code already exists");
    if (projects.existsByNameIgnoreCase(request.name())) conflict("Project name already exists");
    Project project = new Project(request.code(), request.name(), request.description(),
        findDepartment(request.departmentId()), findManager(request.managerId()),
        request.active() == null || request.active());
    return ProjectDtos.Response.from(projects.save(project));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @Transactional
  public ProjectDtos.Response update(@PathVariable UUID id,
      @Valid @RequestBody ProjectDtos.SaveRequest request) {
    Project project = projects.findById(id).orElseThrow(() -> notFound("Project not found"));
    if (!project.getCode().equalsIgnoreCase(request.code())) conflict("Project code cannot be changed");
    if (!project.getName().equalsIgnoreCase(request.name()) && projects.existsByNameIgnoreCase(request.name())) {
      conflict("Project name already exists");
    }
    project.update(request.name(), request.description(), findDepartment(request.departmentId()),
        findManager(request.managerId()), request.active() == null || request.active());
    return ProjectDtos.Response.from(projects.save(project));
  }

  private Department findDepartment(UUID id) {
    Department department = departments.findById(id)
        .orElseThrow(() -> notFound("Department not found"));
    if (!department.isActive()) conflict("Cannot assign a project to an inactive department");
    return department;
  }

  private Employee findManager(UUID id) {
    if (id == null) return null;
    Employee manager = employees.findById(id)
        .orElseThrow(() -> notFound("Manager employee profile not found"));
    if (manager.getStatus() != EmploymentStatus.ACTIVE) conflict("Manager must be active");
    boolean hasManagerAccount = users.findByEmailIgnoreCase(manager.getWorkEmail())
        .map(account -> account.isEnabled() && account.getRoles().contains(Role.MANAGER))
        .orElse(false);
    if (!hasManagerAccount) conflict("Assign an enabled MANAGER login to this employee before selecting them as a project manager");
    return manager;
  }

  private static void conflict(String message) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }
}

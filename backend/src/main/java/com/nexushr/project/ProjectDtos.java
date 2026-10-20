package com.nexushr.project;

import com.nexushr.department.Department;
import com.nexushr.employee.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ProjectDtos {
  private ProjectDtos() {}

  public record SaveRequest(
      @NotBlank @Size(max = 30) String code,
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String description,
      @NotNull UUID departmentId,
      UUID managerId,
      Boolean active) {}

  public record Response(UUID id, String code, String name, String description, UUID departmentId,
      String departmentName, UUID managerId, String managerName, boolean active) {
    public static Response from(Project project) {
      Department department = project.getDepartment();
      Employee manager = project.getManager();
      return new Response(project.getId(), project.getCode(), project.getName(), project.getDescription(),
          department.getId(), department.getName(), manager == null ? null : manager.getId(),
          manager == null ? null : manager.getFirstName() + " " + manager.getLastName(), project.isActive());
    }
  }
}

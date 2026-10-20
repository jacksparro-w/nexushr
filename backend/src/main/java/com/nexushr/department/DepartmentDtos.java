package com.nexushr.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import com.nexushr.employee.Employee;

public final class DepartmentDtos {
  private DepartmentDtos() {}

  public record SaveRequest(
      @NotBlank @Size(max = 30) String code,
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String description,
      Boolean active,
      UUID managerId) {}

  public record Response(UUID id, String code, String name, String description, boolean active,
      UUID managerId, String managerName) {
    public static Response from(Department department) {
      return new Response(department.getId(), department.getCode(), department.getName(),
          department.getDescription(), department.isActive(),
          department.getManager() == null ? null : department.getManager().getId(),
          managerName(department.getManager()));
    }

    private static String managerName(Employee employee) {
      return employee == null ? null : employee.getFirstName() + " " + employee.getLastName();
    }
  }
}

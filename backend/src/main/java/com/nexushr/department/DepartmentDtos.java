package com.nexushr.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class DepartmentDtos {
  private DepartmentDtos() {}

  public record SaveRequest(
      @NotBlank @Size(max = 30) String code,
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String description,
      Boolean active) {}

  public record Response(UUID id, String code, String name, String description, boolean active) {
    public static Response from(Department department) {
      return new Response(department.getId(), department.getCode(), department.getName(),
          department.getDescription(), department.isActive());
    }
  }
}

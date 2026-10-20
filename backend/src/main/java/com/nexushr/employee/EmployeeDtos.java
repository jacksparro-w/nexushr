package com.nexushr.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class EmployeeDtos {
  private EmployeeDtos() {}

  public record SaveRequest(
      @NotBlank @Size(max = 30) String employeeCode,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotBlank @Email @Size(max = 320) String workEmail,
      @Size(max = 40) String phone,
      @NotBlank @Size(max = 120) String designation,
      @NotNull UUID departmentId,
      UUID managerId,
      EmploymentStatus status,
      OnboardingStatus onboardingStatus,
      @NotNull LocalDate hireDate) {}

  public record Response(UUID id, String employeeCode, String firstName, String lastName,
      String workEmail, String phone, String designation, UUID departmentId,
      String departmentName, UUID managerId, String managerName, EmploymentStatus status,
      OnboardingStatus onboardingStatus, LocalDate hireDate) {
    public static Response from(Employee employee) {
      return new Response(employee.getId(), employee.getEmployeeCode(), employee.getFirstName(),
          employee.getLastName(), employee.getWorkEmail(), employee.getPhone(), employee.getDesignation(),
          employee.getDepartment().getId(), employee.getDepartment().getName(),
          employee.getManager() == null ? null : employee.getManager().getId(),
          employee.getManager() == null ? null : employee.getManager().getFirstName() + " " + employee.getManager().getLastName(),
          employee.getStatus(), employee.getOnboardingStatus(), employee.getHireDate());
    }
  }

  public record ManagerOption(UUID id, String employeeCode, String firstName, String lastName,
      String workEmail, UUID departmentId, String departmentName) {
    public static ManagerOption from(Employee employee) {
      return new ManagerOption(employee.getId(), employee.getEmployeeCode(), employee.getFirstName(),
          employee.getLastName(), employee.getWorkEmail(), employee.getDepartment().getId(),
          employee.getDepartment().getName());
    }
  }

  public record DocumentRequest(@NotBlank @Size(max = 40) String documentType,
      @NotBlank @Size(max = 255) String fileName,
      @NotBlank @Size(max = 120) String contentType,
      @NotNull @jakarta.validation.constraints.PositiveOrZero Long sizeBytes) {}

  public record DocumentResponse(UUID id, String documentType, String fileName,
      String contentType, long sizeBytes, Instant uploadedAt) {
    public static DocumentResponse from(EmployeeDocument d) {
      return new DocumentResponse(d.getId(), d.getDocumentType(), d.getFileName(),
          d.getContentType(), d.getSizeBytes(), d.getUploadedAt());
    }
  }
}

package com.nexushr.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {
  List<EmployeeDocument> findAllByEmployeeIdOrderByUploadedAtDesc(UUID employeeId);
  java.util.Optional<EmployeeDocument> findByIdAndEmployeeId(UUID id, UUID employeeId);
}

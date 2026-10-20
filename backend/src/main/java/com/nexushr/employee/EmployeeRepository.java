package com.nexushr.employee;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
  java.util.Optional<Employee> findByWorkEmailIgnoreCase(String email);
  Page<Employee> findByManagerWorkEmailIgnoreCase(String email, Pageable pageable);
  boolean existsByManagerId(UUID id);
  boolean existsByDepartmentId(UUID id);
  boolean existsByEmployeeCodeIgnoreCase(String code);
  boolean existsByWorkEmailIgnoreCase(String email);
}

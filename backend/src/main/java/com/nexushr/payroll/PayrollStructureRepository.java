package com.nexushr.payroll;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollStructureRepository extends JpaRepository<PayrollStructure, UUID> {
  boolean existsByEmployeeId(UUID employeeId);
  Optional<PayrollStructure> findByEmployeeId(UUID employeeId);
}

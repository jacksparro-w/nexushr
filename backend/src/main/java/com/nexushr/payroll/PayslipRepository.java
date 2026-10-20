package com.nexushr.payroll;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
  boolean existsByEmployeeId(UUID employeeId);
  long countByPayrollRunId(UUID runId);
  Page<Payslip> findByPayrollRunIdOrderByEmployeeLastNameAscEmployeeFirstNameAsc(UUID runId, Pageable pageable);
  List<Payslip> findByPayrollRunIdOrderByEmployeeLastNameAscEmployeeFirstNameAsc(UUID runId);
  List<Payslip> findByEmployeeIdOrderByPayrollRunPayrollMonthDesc(UUID employeeId);
  Optional<Payslip> findByIdAndEmployeeId(UUID id, UUID employeeId);
  Optional<Payslip> findByIdAndPayrollRunId(UUID id, UUID runId);
}

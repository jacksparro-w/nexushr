package com.nexushr.payroll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipCorrectionRepository extends JpaRepository<PayslipCorrection, UUID> {
  List<PayslipCorrection> findByPayslipIdOrderByCorrectedAtDesc(UUID payslipId);
}

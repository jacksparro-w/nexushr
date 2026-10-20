package com.nexushr.payroll;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
  boolean existsByPayrollMonth(String month);
  List<PayrollRun> findAllByOrderByPayrollMonthDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from PayrollRun r where r.id = :id")
  Optional<PayrollRun> findForUpdate(@Param("id") UUID id);
}

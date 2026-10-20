package com.nexushr.leave;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
  boolean existsByEmployeeId(UUID employeeId);
  List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeNameAsc(UUID employeeId, int year);
  Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from LeaveBalance b where b.employee.id = :employeeId and b.leaveType.id = :typeId and b.year = :year")
  Optional<LeaveBalance> findForUpdate(@Param("employeeId") UUID employeeId, @Param("typeId") UUID typeId, @Param("year") int year);
}

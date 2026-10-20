package com.nexushr.leave;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
  boolean existsByEmployeeId(UUID employeeId);
  List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
  List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);
  List<LeaveRequest> findAllByOrderByCreatedAtDesc();
  List<LeaveRequest> findByEmployeeManagerWorkEmailIgnoreCaseOrderByCreatedAtDesc(String email);

  @Query("select r from LeaveRequest r where r.employee.id = :employeeId and r.status in :statuses and r.startDate <= :endDate and r.endDate >= :startDate order by r.createdAt desc")
  List<LeaveRequest> findOverlapping(@Param("employeeId") UUID employeeId, @Param("statuses") List<LeaveStatus> statuses,
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  @Query("select coalesce(sum(r.days), 0) from LeaveRequest r where r.employee.id = :employeeId and r.leaveType.id = :typeId and year(r.startDate) = :year and r.status = :status")
  java.math.BigDecimal sumDays(@Param("employeeId") UUID employeeId, @Param("typeId") UUID typeId,
      @Param("year") int year, @Param("status") LeaveStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from LeaveRequest r where r.id = :id")
  Optional<LeaveRequest> findForUpdate(@Param("id") UUID id);
}

package com.nexushr.attendance;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
  Page<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescClockInAtDesc(
      UUID employeeId, LocalDate from, LocalDate to, Pageable pageable);
  Page<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateDescClockInAtDesc(
      LocalDate from, LocalDate to, Pageable pageable);
  List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescClockInAtDesc(
      UUID employeeId, LocalDate from, LocalDate to);
  List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateDescClockInAtDesc(LocalDate from, LocalDate to);
  List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
      UUID employeeId, LocalDate from, LocalDate to);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AttendanceRecord a where a.employee.id = :employeeId and a.clockOutAt is null order by a.clockInAt desc")
  List<AttendanceRecord> lockOpenRecords(@Param("employeeId") UUID employeeId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AttendanceRecord a where a.id = :id")
  Optional<AttendanceRecord> findByIdForUpdate(@Param("id") UUID id);
  boolean existsByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
  boolean existsByEmployeeId(UUID employeeId);
  Optional<AttendanceRecord> findFirstByEmployeeIdAndClockOutAtIsNullOrderByClockInAtDesc(UUID employeeId);
  Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
  @Query(value = "select count(*) as days_worked, "
      + "coalesce(sum(case when is_late then 1 else 0 end), 0) as late_days, "
      + "coalesce(sum(case when clock_out_at is not null then floor(extract(epoch from (clock_out_at - clock_in_at)) / 60) else 0 end), 0) as total_worked_minutes, "
      + "coalesce(sum(case when clock_out_at is not null then 1 else 0 end), 0) as completed_days "
      + "from attendance_records where work_date between :from and :to", nativeQuery = true)
  List<Object[]> summarizeManaged(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(value = "select count(*) as days_worked, "
      + "coalesce(sum(case when a.is_late then 1 else 0 end), 0) as late_days, "
      + "coalesce(sum(case when a.clock_out_at is not null then floor(extract(epoch from (a.clock_out_at - a.clock_in_at)) / 60) else 0 end), 0) as total_worked_minutes, "
      + "coalesce(sum(case when a.clock_out_at is not null then 1 else 0 end), 0) as completed_days "
      + "from attendance_records a join employees e on e.id = a.employee_id "
      + "where e.manager_id = :managerId and a.work_date between :from and :to", nativeQuery = true)
  List<Object[]> summarizeTeam(@Param("managerId") java.util.UUID managerId,
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}

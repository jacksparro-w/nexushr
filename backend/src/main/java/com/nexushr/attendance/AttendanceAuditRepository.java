package com.nexushr.attendance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceAuditRepository extends JpaRepository<AttendanceAudit, UUID> {
  List<AttendanceAudit> findByAttendanceIdOrderByCorrectedAtDesc(UUID attendanceId);
}

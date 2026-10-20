package com.nexushr.attendance;

import com.nexushr.auth.UserAccount;
import com.nexushr.auth.UserRepository;
import com.nexushr.employee.Employee;
import com.nexushr.employee.EmployeeRepository;
import com.nexushr.employee.EmploymentStatus;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {
  private final AttendanceRepository attendance;
  private final AttendanceAuditRepository audits;
  private final EmployeeRepository employees;
  private final UserRepository users;
  private final EntityManager entityManager;
  private final Clock clock;
  private final ZoneId zone;
  private final AttendanceRules rules;

  public AttendanceService(AttendanceRepository attendance, AttendanceAuditRepository audits,
      EmployeeRepository employees, UserRepository users, EntityManager entityManager,
      @Value("${app.attendance.time-zone:Asia/Kolkata}") String timeZone,
      @Value("${app.attendance.workday-start:09:00}") LocalTime workdayStart,
      @Value("${app.attendance.grace-minutes:0}") int graceMinutes) {
    this.attendance = attendance;
    this.audits = audits;
    this.employees = employees;
    this.users = users;
    this.entityManager = entityManager;
    this.clock = Clock.systemUTC();
    this.zone = ZoneId.of(timeZone);
    this.rules = new AttendanceRules(zone, workdayStart, graceMinutes);
  }

  @Transactional
  public AttendanceDtos.RecordResponse clockIn(String email) {
    Employee employee = activeEmployeeForUpdate(email);
    if (!attendance.lockOpenRecords(employee.getId()).isEmpty()) conflict("You already have an open attendance shift");
    Instant now = clock.instant();
    LocalDate date = rules.workDate(now);
    if (attendance.existsByEmployeeIdAndWorkDate(employee.getId(), date)) conflict("Attendance is already recorded for today");
    boolean late = rules.isLate(now);
    AttendanceRecord record = attendance.saveAndFlush(new AttendanceRecord(employee, date, now, late));
    return AttendanceDtos.RecordResponse.from(record);
  }

  @Transactional
  public AttendanceDtos.RecordResponse clockOut(String email) {
    Employee employee = activeEmployeeForUpdate(email);
    List<AttendanceRecord> open = attendance.lockOpenRecords(employee.getId());
    if (open.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No open attendance shift to clock out");
    AttendanceRecord record = open.getFirst();
    Instant now = clock.instant();
    if (!now.isAfter(record.getClockInAt())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Clock-out must be after clock-in");
    record.clockOut(now);
    return AttendanceDtos.RecordResponse.from(attendance.save(record));
  }

  @Transactional(readOnly = true)
  public List<AttendanceDtos.RecordResponse> ownHistory(String email, LocalDate from, LocalDate to) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(email))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found"));
    validateRange(from, to);
    return attendance.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescClockInAtDesc(employee.getId(), from, to)
        .stream().map(AttendanceDtos.RecordResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public AttendanceDtos.PageResponse managedHistory(UUID employeeId, LocalDate from, LocalDate to, int page, int size) {
    validateRange(from, to);
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
    }
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("workDate"), Sort.Order.desc("clockInAt")));
    var records = employeeId == null
        ? attendance.findByWorkDateBetweenOrderByWorkDateDescClockInAtDesc(from, to, pageable)
        : attendance.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescClockInAtDesc(employeeId, from, to, pageable);
    return new AttendanceDtos.PageResponse(records.map(AttendanceDtos.RecordResponse::from).getContent(),
        records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AttendanceDtos.SummaryResponse summary(String email, YearMonth month) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(email))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found"));
    List<AttendanceRecord> records = attendance.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
        employee.getId(), month.atDay(1), month.atEndOfMonth());
    return new AttendanceDtos.SummaryResponse(month, records.size(), records.stream().filter(AttendanceRecord::isLate).count(),
        records.stream().filter(r -> r.getClockOutAt() != null)
            .mapToLong(r -> Duration.between(r.getClockInAt(), r.getClockOutAt()).toMinutes()).sum(),
        records.stream().filter(r -> r.getClockOutAt() != null).count());
  }

  @Transactional(readOnly = true)
  public AttendanceDtos.SummaryResponse managedSummary(YearMonth month) {
    Object[] totals = attendance.summarizeManaged(month.atDay(1), month.atEndOfMonth()).getFirst();
    return new AttendanceDtos.SummaryResponse(month, number(totals[0]), number(totals[1]),
        number(totals[2]), number(totals[3]));
  }

  @Transactional(readOnly = true)
  public AttendanceDtos.SummaryResponse teamSummary(String principal, YearMonth month) {
    Employee manager = employees.findByWorkEmailIgnoreCase(resolveEmail(principal))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found"));
    Object[] totals = attendance.summarizeTeam(manager.getId(), month.atDay(1), month.atEndOfMonth()).getFirst();
    return new AttendanceDtos.SummaryResponse(month, number(totals[0]), number(totals[1]),
        number(totals[2]), number(totals[3]));
  }

  @Transactional(readOnly = true)
  public AttendanceDtos.RecordResponse current(String principal) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(principal))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found"));
    AttendanceRecord current = attendance.findFirstByEmployeeIdAndClockOutAtIsNullOrderByClockInAtDesc(employee.getId())
        .orElseGet(() -> attendance.findByEmployeeIdAndWorkDate(employee.getId(), rules.workDate(clock.instant())).orElse(null));
    return current == null ? null : AttendanceDtos.RecordResponse.from(current);
  }

  @Transactional
  public AttendanceDtos.RecordResponse correct(UUID attendanceId, AttendanceDtos.CorrectionRequest request, String actorEmail) {
    AttendanceRecord record = attendance.findByIdForUpdate(attendanceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found"));
    if (request.clockOutAt() != null && !request.clockOutAt().isAfter(request.clockInAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clock-out must be after clock-in");
    }
    LocalDate correctionDate = request.clockInAt().atZone(zone).toLocalDate();
    if (!record.getWorkDate().equals(correctionDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction must keep the attendance work date unchanged");
    }
    String reason = request.reason().trim();
    if (reason.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A correction reason is required");
    Instant now = clock.instant();
    if (request.clockInAt().isAfter(now) || (request.clockOutAt() != null && request.clockOutAt().isAfter(now))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attendance corrections cannot use future timestamps");
    }
    if (request.clockOutAt() == null && attendance.lockOpenRecords(record.getEmployee().getId()).stream()
        .anyMatch(open -> !open.getId().equals(record.getId()))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has another open attendance shift");
    }
    Instant oldIn = record.getClockInAt();
    Instant oldOut = record.getClockOutAt();
    boolean late = rules.isLate(request.clockInAt());
    record.correct(request.clockInAt(), request.clockOutAt(), late);
    audits.save(new AttendanceAudit(record, oldIn, oldOut, request.clockInAt(), request.clockOutAt(), reason, resolveEmail(actorEmail)));
    entityManager.flush();
    return AttendanceDtos.RecordResponse.from(record);
  }

  @Transactional(readOnly = true)
  public List<AttendanceDtos.AuditResponse> audit(UUID attendanceId) {
    if (!attendance.existsById(attendanceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found");
    return audits.findByAttendanceIdOrderByCorrectedAtDesc(attendanceId).stream().map(AttendanceDtos.AuditResponse::from).toList();
  }

  private Employee activeEmployeeForUpdate(String email) {
    Employee employee = employees.findByWorkEmailIgnoreCase(resolveEmail(email))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee profile not found"));
    Employee locked = entityManager.find(Employee.class, employee.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (locked.getStatus() != EmploymentStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active employees can record attendance");
    return locked;
  }

  private String resolveEmail(String principal) {
    try {
      return users.findById(UUID.fromString(principal)).map(UserAccount::getEmail).orElse(principal);
    } catch (IllegalArgumentException notUuid) {
      return principal;
    }
  }

  private static long number(Object value) { return ((Number) value).longValue(); }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from) || from.plusDays(92).isBefore(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range must be ordered and no longer than 93 days");
    }
  }

  private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

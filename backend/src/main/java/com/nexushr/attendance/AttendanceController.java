package com.nexushr.attendance;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {
  private final AttendanceService service;
  public AttendanceController(AttendanceService service) { this.service = service; }

  @PostMapping("/clock-in")
  @ResponseStatus(HttpStatus.CREATED)
  public AttendanceDtos.RecordResponse clockIn(Authentication auth) { return service.clockIn(auth.getName()); }

  @PostMapping("/clock-out")
  public AttendanceDtos.RecordResponse clockOut(Authentication auth) { return service.clockOut(auth.getName()); }

  @GetMapping("/me")
  public List<AttendanceDtos.RecordResponse> ownHistory(
      Authentication auth,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.ownHistory(auth.getName(), from, to);
  }

  @GetMapping("/me/summary")
  public AttendanceDtos.SummaryResponse summary(Authentication auth,
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return service.summary(auth.getName(), month);
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public AttendanceDtos.SummaryResponse managedSummary(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return service.managedSummary(month);
  }

  @GetMapping("/team/summary")
  @PreAuthorize("hasRole('MANAGER')")
  public AttendanceDtos.SummaryResponse teamSummary(Authentication auth,
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return service.teamSummary(auth.getName(), month);
  }

  @GetMapping("/me/current")
  public ResponseEntity<AttendanceDtos.RecordResponse> current(Authentication auth) {
    AttendanceDtos.RecordResponse record = service.current(auth.getName());
    return record == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(record);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public AttendanceDtos.PageResponse managedHistory(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return service.managedHistory(employeeId, from, to, page, size);
  }

  @PatchMapping("/{id}/correction")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public AttendanceDtos.RecordResponse correct(@PathVariable UUID id, @Valid @RequestBody AttendanceDtos.CorrectionRequest request,
      Authentication auth) {
    return service.correct(id, request, auth.getName());
  }

  @GetMapping("/{id}/audit")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public List<AttendanceDtos.AuditResponse> audit(@PathVariable UUID id) { return service.audit(id); }
}

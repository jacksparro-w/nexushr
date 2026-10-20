package com.nexushr.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

final class AttendanceRules {
  private final ZoneId zone;
  private final LocalTime workdayStart;
  private final int graceMinutes;

  AttendanceRules(ZoneId zone, LocalTime workdayStart, int graceMinutes) {
    if (graceMinutes < 0 || graceMinutes > 180) {
      throw new IllegalArgumentException("Attendance grace minutes must be between 0 and 180");
    }
    this.zone = zone;
    this.workdayStart = workdayStart;
    this.graceMinutes = graceMinutes;
  }

  LocalDate workDate(Instant instant) { return instant.atZone(zone).toLocalDate(); }

  boolean isLate(Instant instant) {
    LocalTime threshold = workdayStart.plusMinutes(graceMinutes);
    return instant.atZone(zone).toLocalTime().isAfter(threshold);
  }
}

package com.nexushr.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AttendanceRulesTest {
  private final AttendanceRules rules =
      new AttendanceRules(ZoneId.of("Asia/Kolkata"), LocalTime.of(9, 0), 15);

  @Test
  void gracePeriodBoundaryIsConsistentInConfiguredTimeZone() {
    assertFalse(rules.isLate(Instant.parse("2026-10-18T03:44:59Z")));
    assertFalse(rules.isLate(Instant.parse("2026-10-18T03:45:00Z")));
    assertTrue(rules.isLate(Instant.parse("2026-10-18T03:45:01Z")));
  }

  @Test
  void workDateFollowsConfiguredZoneAcrossUtcMidnightBoundary() {
    assertEquals(LocalDate.parse("2026-03-31"), rules.workDate(Instant.parse("2026-03-31T18:29:59Z")));
    assertEquals(LocalDate.parse("2026-04-01"), rules.workDate(Instant.parse("2026-03-31T18:30:00Z")));
  }
}

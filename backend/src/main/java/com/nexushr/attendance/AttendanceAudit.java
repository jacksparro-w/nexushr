package com.nexushr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_audits")
public class AttendanceAudit {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "attendance_id", nullable = false) private AttendanceRecord attendance;
  @Column(name = "previous_clock_in_at", nullable = false) private Instant previousClockInAt;
  @Column(name = "previous_clock_out_at") private Instant previousClockOutAt;
  @Column(name = "corrected_clock_in_at", nullable = false) private Instant correctedClockInAt;
  @Column(name = "corrected_clock_out_at") private Instant correctedClockOutAt;
  @Column(nullable = false, length = 500) private String reason;
  @Column(name = "corrected_by", nullable = false, length = 320) private String correctedBy;
  @Column(name = "corrected_at", nullable = false, updatable = false) private Instant correctedAt = Instant.now();

  protected AttendanceAudit() {}

  AttendanceAudit(AttendanceRecord attendance, Instant oldIn, Instant oldOut, Instant newIn,
      Instant newOut, String reason, String correctedBy) {
    this.attendance = attendance;
    previousClockInAt = oldIn;
    previousClockOutAt = oldOut;
    correctedClockInAt = newIn;
    correctedClockOutAt = newOut;
    this.reason = reason;
    this.correctedBy = correctedBy;
  }

  public UUID getId() { return id; }
  public Instant getPreviousClockInAt() { return previousClockInAt; }
  public Instant getPreviousClockOutAt() { return previousClockOutAt; }
  public Instant getCorrectedClockInAt() { return correctedClockInAt; }
  public Instant getCorrectedClockOutAt() { return correctedClockOutAt; }
  public String getReason() { return reason; }
  public String getCorrectedBy() { return correctedBy; }
  public Instant getCorrectedAt() { return correctedAt; }
}

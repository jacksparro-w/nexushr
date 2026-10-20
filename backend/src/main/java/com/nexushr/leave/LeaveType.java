package com.nexushr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leave_types")
public class LeaveType {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(nullable = false, unique = true, length = 30) private String code;
  @Column(nullable = false, length = 100) private String name;
  @Column(length = 500) private String description;
  @Column(nullable = false) private boolean paid;
  @Column(nullable = false) private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected LeaveType() {}

  LeaveType(LeaveDtos.LeaveTypeRequest request) { update(request); }

  void update(LeaveDtos.LeaveTypeRequest request) {
    code = request.code().trim().toUpperCase();
    name = request.name().trim();
    description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
    paid = request.paid();
    active = request.active() == null || request.active();
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public boolean isPaid() { return paid; }
  public boolean isActive() { return active; }
}

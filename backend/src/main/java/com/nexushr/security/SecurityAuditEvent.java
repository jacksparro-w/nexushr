package com.nexushr.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_log")
public class SecurityAuditEvent {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "actor_id") private UUID actorId;
  @Column(nullable = false, length = 40) private String action;
  @Column(name = "resource_path", nullable = false, length = 300) private String resourcePath;
  @Column(name = "http_status", nullable = false) private int httpStatus;
  @Column(name = "occurred_at", nullable = false) private Instant occurredAt = Instant.now();
  protected SecurityAuditEvent() {}
  SecurityAuditEvent(UUID actorId, String action, String resourcePath, int httpStatus) {
    this.actorId = actorId; this.action = action; this.resourcePath = resourcePath; this.httpStatus = httpStatus;
  }
  public UUID getId() { return id; }
  public UUID getActorId() { return actorId; }
  public String getAction() { return action; }
  public String getResourcePath() { return resourcePath; }
  public int getHttpStatus() { return httpStatus; }
  public Instant getOccurredAt() { return occurredAt; }
}

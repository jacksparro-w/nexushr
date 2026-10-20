package com.nexushr.security;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {
  private final SecurityAuditRepository events;
  public SecurityAuditService(SecurityAuditRepository events) { this.events = events; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String actor, String action, String path, int status) {
    UUID actorId = null;
    try { if (actor != null) actorId = UUID.fromString(actor); } catch (IllegalArgumentException ignored) {}
    events.save(new SecurityAuditEvent(actorId, action, path.length() > 300 ? path.substring(0, 300) : path, status));
  }

  @Transactional(readOnly = true)
  public java.util.List<AuditEventResponse> latest() {
    return events.findTop100ByOrderByOccurredAtDesc().stream()
        .map(event -> new AuditEventResponse(event.getId(), event.getActorId(), event.getAction(),
            event.getResourcePath(), event.getHttpStatus(), event.getOccurredAt())).toList();
  }

  public record AuditEventResponse(UUID id, UUID actorId, String action, String resourcePath,
      int httpStatus, java.time.Instant occurredAt) {}
}

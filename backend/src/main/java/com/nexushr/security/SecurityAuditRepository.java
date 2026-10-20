package com.nexushr.security;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, UUID> {
  java.util.List<SecurityAuditEvent> findTop100ByOrderByOccurredAtDesc();
}

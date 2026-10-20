package com.nexushr.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
  List<EmailOutbox> findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
      EmailOutbox.Status status, Instant due);
  boolean existsByDedupeKey(String key);
}

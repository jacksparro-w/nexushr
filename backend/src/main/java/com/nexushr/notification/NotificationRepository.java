package com.nexushr.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop100ByRecipientIdOrderByCreatedAtDesc(UUID userId);
  long countByRecipientIdAndReadAtIsNull(UUID userId);
  Optional<Notification> findByIdAndRecipientId(UUID id, UUID userId);
  List<Notification> findByRecipientIdAndReadAtIsNull(UUID userId);
  boolean existsByDedupeKey(String key);
}

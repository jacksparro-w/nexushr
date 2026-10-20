package com.nexushr.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
  List<Announcement> findTop50ByOrderByCreatedAtDesc();
}

package com.nexushr.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
  private NotificationDtos() {}
  public record NotificationResponse(UUID id, String title, String message, String category,
      Instant createdAt, Instant readAt) {
    static NotificationResponse from(Notification value) {
      return new NotificationResponse(value.getId(), value.getTitle(), value.getMessage(),
          value.getCategory(), value.getCreatedAt(), value.getReadAt());
    }
  }
  public record AnnouncementRequest(@NotBlank @Size(max = 180) String title,
      @NotBlank @Size(max = 4000) String body) {}
  public record AnnouncementResponse(UUID id, String title, String body, String createdBy, Instant createdAt) {
    static AnnouncementResponse from(Announcement value) {
      return new AnnouncementResponse(value.getId(), value.getTitle(), value.getBody(),
          value.getCreatedBy(), value.getCreatedAt());
    }
  }
  public record UnreadCount(long count) {}
}

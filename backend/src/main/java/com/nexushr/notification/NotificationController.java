package com.nexushr.notification;

import com.nexushr.notification.NotificationDtos.AnnouncementRequest;
import com.nexushr.notification.NotificationDtos.AnnouncementResponse;
import com.nexushr.notification.NotificationDtos.NotificationResponse;
import com.nexushr.notification.NotificationDtos.UnreadCount;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service = service; }

  @GetMapping("/notifications")
  public List<NotificationResponse> notifications(Authentication auth) {
    return service.list(UUID.fromString(auth.getName()));
  }

  @GetMapping("/notifications/unread-count")
  public UnreadCount unreadCount(Authentication auth) {
    return new UnreadCount(service.unreadCount(UUID.fromString(auth.getName())));
  }

  @PatchMapping("/notifications/{id}/read")
  public NotificationResponse markRead(Authentication auth, @PathVariable UUID id) {
    return service.markRead(UUID.fromString(auth.getName()), id);
  }

  @PostMapping("/notifications/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllRead(Authentication auth) { service.markAllRead(UUID.fromString(auth.getName())); }

  @GetMapping("/announcements")
  public List<AnnouncementResponse> announcements() { return service.announcements(); }

  @PostMapping("/announcements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public AnnouncementResponse createAnnouncement(@Valid @RequestBody AnnouncementRequest request,
      Authentication auth) {
    return service.createAnnouncement(request.title(), request.body(), auth.getName());
  }
}

package com.nexushr.notification;

import com.nexushr.auth.UserAccount;
import com.nexushr.auth.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class NotificationService {
  private final NotificationRepository notifications;
  private final AnnouncementRepository announcements;
  private final EmailOutboxRepository outbox;
  private final UserRepository users;
  private final NotificationSocketHub socketHub;

  public NotificationService(NotificationRepository notifications, AnnouncementRepository announcements,
      EmailOutboxRepository outbox, UserRepository users, NotificationSocketHub socketHub) {
    this.notifications = notifications; this.announcements = announcements;
    this.outbox = outbox; this.users = users; this.socketHub = socketHub;
  }

  @Transactional(readOnly = true)
  public List<NotificationDtos.NotificationResponse> list(UUID userId) {
    return notifications.findTop100ByRecipientIdOrderByCreatedAtDesc(userId).stream()
        .map(NotificationDtos.NotificationResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) { return notifications.countByRecipientIdAndReadAtIsNull(userId); }

  @Transactional
  public NotificationDtos.NotificationResponse markRead(UUID userId, UUID notificationId) {
    Notification item = notifications.findByIdAndRecipientId(notificationId, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    item.markRead();
    return NotificationDtos.NotificationResponse.from(item);
  }

  @Transactional
  public void markAllRead(UUID userId) {
    notifications.findByRecipientIdAndReadAtIsNull(userId).forEach(Notification::markRead);
  }

  @Transactional
  public NotificationDtos.AnnouncementResponse createAnnouncement(String title, String body, String actor) {
    Announcement announcement = announcements.saveAndFlush(new Announcement(title, body, actor));
    for (UserAccount user : users.findAll()) {
      String key = "announcement:" + announcement.getId() + ":" + user.getId();
      createNotification(user, "Company announcement", announcement.getTitle(), "ANNOUNCEMENT", key);
      createEmail(user.getEmail(), announcement.getTitle(), announcement.getBody(), key);
    }
    return NotificationDtos.AnnouncementResponse.from(announcement);
  }

  @Transactional(readOnly = true)
  public List<NotificationDtos.AnnouncementResponse> announcements() {
    return announcements.findTop50ByOrderByCreatedAtDesc().stream()
        .map(NotificationDtos.AnnouncementResponse::from).toList();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deliverToEmail(String email, String title, String message, String category,
      String dedupeKey, String emailSubject, String emailBody) {
    users.findByEmailIgnoreCase(email).ifPresent(user ->
        createNotification(user, title, message, category, dedupeKey));
    createEmail(email, emailSubject, emailBody, dedupeKey);
  }

  private void createNotification(UserAccount user, String title, String message, String category, String key) {
    if (notifications.existsByDedupeKey(key)) return;
    Notification item = notifications.saveAndFlush(new Notification(user, title, message, category, key));
    NotificationDtos.NotificationResponse dto = NotificationDtos.NotificationResponse.from(item);
    UUID recipient = user.getId();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { socketHub.send(recipient, dto); }
      });
    }
  }

  private void createEmail(String recipient, String subject, String body, String key) {
    String emailKey = "email:" + key;
    if (outbox.existsByDedupeKey(emailKey)) return;
    try { outbox.save(new EmailOutbox(recipient, subject, body, emailKey)); }
    catch (DataIntegrityViolationException duplicate) {
      // A repeated domain event must not duplicate a queued email.
    }
  }
}

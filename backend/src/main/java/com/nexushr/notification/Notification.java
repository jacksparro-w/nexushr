package com.nexushr.notification;

import com.nexushr.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_user_id", nullable = false) private UserAccount recipient;
  @Column(nullable = false, length = 180) private String title;
  @Column(nullable = false, length = 1000) private String message;
  @Column(nullable = false, length = 40) private String category;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "read_at") private Instant readAt;
  @Column(name = "dedupe_key", nullable = false, unique = true, length = 255) private String dedupeKey;

  protected Notification() {}

  public Notification(UserAccount recipient, String title, String message, String category, String dedupeKey) {
    this.recipient = recipient;
    this.title = title;
    this.message = message;
    this.category = category;
    this.dedupeKey = dedupeKey;
  }

  public void markRead() { if (readAt == null) readAt = Instant.now(); }
  public UUID getId() { return id; }
  public UserAccount getRecipient() { return recipient; }
  public String getTitle() { return title; }
  public String getMessage() { return message; }
  public String getCategory() { return category; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getReadAt() { return readAt; }
}

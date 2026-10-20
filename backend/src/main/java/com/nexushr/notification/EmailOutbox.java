package com.nexushr.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {
  public enum Status { PENDING, SENT, DEAD }
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "recipient_email", nullable = false, length = 320) private String recipientEmail;
  @Column(nullable = false, length = 180) private String subject;
  @Column(nullable = false, length = 4000) private String body;
  @Column(name = "dedupe_key", nullable = false, unique = true, length = 255) private String dedupeKey;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
  @Column(nullable = false) private int attempts;
  @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt = Instant.now();
  @Column(name = "last_error", length = 120) private String lastError;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "sent_at") private Instant sentAt;

  protected EmailOutbox() {}
  public EmailOutbox(String recipientEmail, String subject, String body, String dedupeKey) {
    this.recipientEmail = recipientEmail; this.subject = subject; this.body = body; this.dedupeKey = dedupeKey;
  }
  public void sent() { status = Status.SENT; sentAt = Instant.now(); lastError = null; }
  public void failed(String error, Instant retryAt) {
    attempts++;
    lastError = error;
    if (attempts >= 8) status = Status.DEAD;
    else nextAttemptAt = retryAt;
  }
  public UUID getId() { return id; }
  public String getRecipientEmail() { return recipientEmail; }
  public String getSubject() { return subject; }
  public String getBody() { return body; }
  public int getAttempts() { return attempts; }
}

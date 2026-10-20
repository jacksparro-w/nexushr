package com.nexushr.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
public class Announcement {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(nullable = false, length = 180) private String title;
  @Column(nullable = false, length = 4000) private String body;
  @Column(name = "created_by", nullable = false, length = 320) private String createdBy;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

  protected Announcement() {}
  public Announcement(String title, String body, String createdBy) {
    this.title = title.trim(); this.body = body.trim(); this.createdBy = createdBy;
  }
  public UUID getId() { return id; }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public String getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
}

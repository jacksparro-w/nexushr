package com.nexushr.notification;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
  private final EmailOutboxRepository outbox;
  private final ObjectProvider<JavaMailSender> senderProvider;
  private final String from;

  public EmailOutboxWorker(EmailOutboxRepository outbox, ObjectProvider<JavaMailSender> senderProvider,
      @Value("${app.notifications.email.from:noreply@nexushr.local}") String from) {
    this.outbox = outbox; this.senderProvider = senderProvider; this.from = from;
  }

  @Scheduled(fixedDelayString = "${app.notifications.email.poll-interval-ms:30000}")
  public void deliverDueMessages() {
    JavaMailSender sender = senderProvider.getIfAvailable();
    if (sender == null) return;
    List<EmailOutbox> due = outbox.findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        EmailOutbox.Status.PENDING, Instant.now());
    for (EmailOutbox item : due) {
      try {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from); mail.setTo(item.getRecipientEmail());
        mail.setSubject(item.getSubject()); mail.setText(item.getBody());
        sender.send(mail);
        item.sent();
      } catch (RuntimeException deliveryFailure) {
        int nextAttempt = item.getAttempts() + 1;
        long delayMinutes = Math.min(360, 1L << Math.min(nextAttempt, 8));
        item.failed(deliveryFailure.getClass().getSimpleName(),
            Instant.now().plus(delayMinutes, TimeUnit.MINUTES.toChronoUnit()));
        log.warn("Email delivery failed for outbox item {} ({}; attempt {})", item.getId(),
            deliveryFailure.getClass().getSimpleName(), nextAttempt);
      }
      outbox.save(item);
    }
  }
}

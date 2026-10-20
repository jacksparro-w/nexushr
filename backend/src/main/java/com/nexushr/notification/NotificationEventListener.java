package com.nexushr.notification;

import com.nexushr.notification.NotificationEvents.LeaveDecision;
import com.nexushr.notification.NotificationEvents.PayrollStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
  private final NotificationService notifications;
  public NotificationEventListener(NotificationService notifications) { this.notifications = notifications; }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void leaveDecision(LeaveDecision event) {
    String message = "Your " + event.leaveType() + " request for " + event.from() + " to " + event.to()
        + " was " + event.status().toLowerCase() + ".";
    String key = "leave:" + event.requestId() + ":" + event.status();
    notifications.deliverToEmail(event.email(), "Leave request " + event.status().toLowerCase(), message,
        "LEAVE", key, "NexusHR leave update", message);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void payrollStatus(PayrollStatus event) {
    for (String email : event.emails()) {
      String message = "Your payroll for " + event.month() + " is now " + event.status().toLowerCase() + ".";
      String key = "payroll:" + event.runId() + ":" + event.status() + ":" + email.toLowerCase();
      notifications.deliverToEmail(email, "Payroll " + event.status().toLowerCase(), message,
          "PAYROLL", key, "NexusHR payroll update", message);
    }
  }
}

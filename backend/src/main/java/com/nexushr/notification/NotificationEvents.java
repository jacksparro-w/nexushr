package com.nexushr.notification;

import java.util.List;

public final class NotificationEvents {
  private NotificationEvents() {}
  public record LeaveDecision(String email, String leaveType, String from, String to,
      String status, String requestId) {}
  public record PayrollStatus(String month, String status, String runId, List<String> emails) {}
}

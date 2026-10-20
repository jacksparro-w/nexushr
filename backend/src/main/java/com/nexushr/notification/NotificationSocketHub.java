package com.nexushr.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class NotificationSocketHub {
  private final ObjectMapper json;
  private final ConcurrentHashMap<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public NotificationSocketHub(ObjectMapper json) { this.json = json; }

  public void connect(UUID userId, WebSocketSession session) {
    sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
  }

  public void disconnect(WebSocketSession session) {
    Object value = session.getAttributes().get("notificationUserId");
    if (!(value instanceof UUID userId)) return;
    Set<WebSocketSession> userSessions = sessions.get(userId);
    if (userSessions != null) {
      userSessions.remove(session);
      if (userSessions.isEmpty()) sessions.remove(userId, userSessions);
    }
  }

  public void send(UUID userId, NotificationDtos.NotificationResponse notification) {
    Set<WebSocketSession> userSessions = sessions.get(userId);
    if (userSessions == null) return;
    try {
      String payload = json.writeValueAsString(new SocketMessage("NOTIFICATION", notification));
      for (WebSocketSession session : userSessions) {
        if (!session.isOpen()) { userSessions.remove(session); continue; }
        try { synchronized (session) { session.sendMessage(new TextMessage(payload)); } }
        catch (IOException failure) { userSessions.remove(session); }
      }
    } catch (IOException ignored) {
      // The persistent notification remains available from the REST API.
    }
  }

  public record SocketMessage(String type, Object notification) {}
}

package com.nexushr.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexushr.auth.TokenService;
import com.nexushr.auth.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper json;
  private final TokenService tokens;
  private final UserRepository users;
  private final NotificationSocketHub hub;

  public NotificationSocketHandler(ObjectMapper json, TokenService tokens, UserRepository users,
      NotificationSocketHub hub) {
    this.json = json; this.tokens = tokens; this.users = users; this.hub = hub;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if (session.getAttributes().containsKey("notificationUserId")) return;
    try {
      JsonNode frame = json.readTree(message.getPayload());
      if (!"AUTH".equals(frame.path("type").asText())) throw new IllegalArgumentException();
      String token = frame.path("accessToken").asText();
      UUID userId = UUID.fromString(tokens.parse(token).getSubject());
      if (!users.existsById(userId)) throw new IllegalArgumentException();
      session.getAttributes().put("notificationUserId", userId);
      hub.connect(userId, session);
      session.sendMessage(new TextMessage("{\"type\":\"READY\"}"));
    } catch (RuntimeException invalid) {
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    hub.disconnect(session);
  }
}

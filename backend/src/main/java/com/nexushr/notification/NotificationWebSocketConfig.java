package com.nexushr.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
  private final NotificationSocketHandler handler;
  private final String frontendOrigin;

  public NotificationWebSocketConfig(NotificationSocketHandler handler,
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    this.handler = handler; this.frontendOrigin = frontendOrigin;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/notifications").setAllowedOrigins(frontendOrigin);
  }
}

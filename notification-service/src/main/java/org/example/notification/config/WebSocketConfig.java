package org.example.notification.config;

import org.example.notification.websocket.NotificationBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationBroadcaster broadcaster;
    private final String endpoint;
    private final String[] allowedOrigins;

    public WebSocketConfig(NotificationBroadcaster broadcaster,
                           @Value("${medical.notifications.websocket-endpoint:/ws/notifications}") String endpoint,
                           @Value("${medical.notifications.allowed-origins:*}") String[] allowedOrigins) {
        this.broadcaster = broadcaster;
        this.endpoint = endpoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(broadcaster, endpoint).setAllowedOriginPatterns(allowedOrigins);
    }
}

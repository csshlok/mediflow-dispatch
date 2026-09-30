package org.example.notification.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Pushes every notification to all connected dashboards. Authentication happens at the gateway.
@Component
public class NotificationBroadcaster extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // The decorator serialises concurrent sends and drops clients that stop reading
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Dropping notification client {}: {}", session.getId(), e.getMessage());
                sessions.remove(session.getId());
            }
        });
    }

    public int connectedClients() {
        return sessions.size();
    }
}

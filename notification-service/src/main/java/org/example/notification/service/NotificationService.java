package org.example.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.notification.websocket.NotificationBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    public void notify(String emergencyInfo, String dispatchInfo, String caseInfo) {
        log.info("[NOTIFICATION] {} | {} | {}", emergencyInfo, dispatchInfo, caseInfo);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headline", emergencyInfo);
        payload.put("detail", dispatchInfo);
        payload.put("reference", caseInfo);
        payload.put("timestamp", Instant.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification", e);
        }

        // Push only once the consumer's transaction commits, so a rolled-back (and retried) event is not sent twice
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcast(json);
                }
            });
        } else {
            broadcaster.broadcast(json);
        }
    }
}

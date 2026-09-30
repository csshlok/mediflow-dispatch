package org.example.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.notification.entity.ProcessedEvent;
import org.example.notification.entity.ProcessedEventId;
import org.example.notification.repository.ProcessedEventRepository;
import org.example.notification.service.NotificationService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.CaseCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class CaseConsumer {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    private static final String CONSUMER_NAME = "notification-service-case-consumer";

    public CaseConsumer(NotificationService notificationService,
                        ProcessedEventRepository processedEventRepository,
                        ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.CASE_EVENTS, groupId = "notification-service-group")
    @Transactional
    public void onCaseCreated(String jsonPayload) {
        try {
            // 3. Translate string to Java record
            CaseCreatedEvent event = objectMapper.readValue(jsonPayload, CaseCreatedEvent.class);

            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            if (processedEventRepository.existsById(id)) {
                System.out.println("⚠️ Duplicate CaseCreated event detected! Skipping: " + eventIdString);
                return;
            }

            notificationService.notify(
                    "Emergency Created: " + event.emergencyId(),
                    "Ambulance " + event.ambulanceId() + " Assigned. Hospital: " + event.hospitalName(),
                    "Case ID: " + event.caseId()
            );

            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
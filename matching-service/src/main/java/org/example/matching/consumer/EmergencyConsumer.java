package org.example.matching.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.matching.entity.ProcessedEvent;
import org.example.matching.entity.ProcessedEventId;
import org.example.matching.repository.ProcessedEventRepository;
import org.example.matching.service.MatchingService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.EmergencyRequestedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class EmergencyConsumer {

    private final MatchingService matchingService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper; // <-- NEW: Bring in the translator

    private static final String CONSUMER_NAME = "matching-service-emergency-consumer";

    public EmergencyConsumer(MatchingService matchingService,
                             ProcessedEventRepository processedEventRepository,
                             ObjectMapper objectMapper) { // <-- NEW
        this.matchingService = matchingService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS, groupId = "matching-service-group")
    @Transactional
    public void onEmergencyRequested(String jsonPayload) {
        try {
            // 1. TRANSLATE: Convert the JSON text back into your Java Record
            EmergencyRequestedEvent event = objectMapper.readValue(jsonPayload, EmergencyRequestedEvent.class);

            // 2. Extract the unique event ID
            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            if (processedEventRepository.existsById(id)) {
                return;
            }

            // 4. BUSINESS LOGIC
            UUID emergencyId = event.emergencyId();
            double latitude = event.latitude();
            double longitude = event.longitude();

            matchingService.processEmergency(emergencyId, latitude, longitude, event.createdAt());

            // 5. THE RECEIPT
            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process incoming emergency event: " + e.getMessage());
            e.printStackTrace(); // Helpful to see the exact error if the JSON is malformed
        }
    }
}

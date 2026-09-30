package org.example.ambulance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ambulance.entity.ProcessedEvent;
import org.example.ambulance.entity.ProcessedEventId;
import org.example.ambulance.repository.ProcessedEventRepository;
import org.example.ambulance.service.AmbulanceService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.enums.AmbulanceStatus;
import org.example.shared.events.DispatchAssignedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class AmbulanceReservationConsumer {

    private final AmbulanceService ambulanceService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    private static final String CONSUMER_NAME = "ambulance-service-reservation-consumer";

    public AmbulanceReservationConsumer(AmbulanceService ambulanceService,
                                        ProcessedEventRepository processedEventRepository,
                                        ObjectMapper objectMapper) { // <-- NEW
        this.ambulanceService = ambulanceService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.DISPATCH_EVENTS, groupId = "ambulance-service-group")
    @Transactional
    public void handleAmbulanceAssigned(String jsonPayload) { // <-- CHANGED: Catch a String
        try {
            // 1. TRANSLATE: Convert the JSON text back into your Java Record
            DispatchAssignedEvent event = objectMapper.readValue(jsonPayload, DispatchAssignedEvent.class);

            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            // 🛡️ 2. Idempotency Check
            if (processedEventRepository.existsById(id)) {
                System.out.println("⚠️ Duplicate dispatch event detected. Skipping: " + eventIdString);
                return;
            }

            System.out.println("🔒 Reserving Ambulance " + event.ambulanceId() + " for Case " + event.emergencyId());

            // 3. Update the Database
            ambulanceService.updateStatus(event.ambulanceId(), AmbulanceStatus.RESERVED);

            // 4. Mark Event as Processed
            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process dispatch event: " + e.getMessage());
            e.printStackTrace(); // Helpful to see the exact mapping error if the JSON changes
        }
    }
}
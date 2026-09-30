package org.example.case_.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.case_.entity.ProcessedEvent;
import org.example.case_.entity.ProcessedEventId;
import org.example.case_.repository.ProcessedEventRepository;
import org.example.case_.service.CaseService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.HospitalAssignedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class HospitalAssignedConsumer {

    private final CaseService caseService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper; // <-- NEW: The Translator

    // The name of this specific consumer for the composite key
    private static final String CONSUMER_NAME = "case-service-hospital-consumer";

    public HospitalAssignedConsumer(CaseService caseService,
                                    ProcessedEventRepository processedEventRepository,
                                    ObjectMapper objectMapper) { // <-- NEW: Inject it
        this.caseService = caseService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.HOSPITAL_EVENTS, groupId = "case-service-group")
    @Transactional // Guarantees the DB save and the business logic happen atomically
    public void consumeHospital(String jsonPayload) { // <-- NEW: Catch the raw String

        try {
            JsonNode node = objectMapper.readTree(jsonPayload);
            if (!node.has("hospitalName")) {
                System.out.println("Case Service received non-HospitalAssigned event on hospital-events; skipping.");
                return;
            }

            // NEW: Translate the JSON string back into your Java record
            HospitalAssignedEvent event = objectMapper.treeToValue(node, HospitalAssignedEvent.class);

            // Extract the unique event ID
            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            // 1. THE BOUNCER: Check if this consumer has already seen this specific event
            if (processedEventRepository.existsById(id)) {
                System.out.println("⚠️ Duplicate event detected! Skipping HospitalAssignedEvent: " + eventIdString);
                return;
            }

            // 2. THE BUSINESS LOGIC: Process the hospital assignment
            System.out.println("🏥 Case Service received new HospitalAssigned for " + event.emergencyId());
            caseService.onHospitalAssigned(event); // Kept your exact method call!

            // 3. THE RECEIPT: Save to DB so we never process it again
            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process hospital assignment: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

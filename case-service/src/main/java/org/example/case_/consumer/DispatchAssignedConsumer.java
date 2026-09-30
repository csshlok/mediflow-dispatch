package org.example.case_.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.case_.entity.ProcessedEvent;
import org.example.case_.entity.ProcessedEventId;
import org.example.case_.repository.ProcessedEventRepository;
import org.example.case_.service.CaseService; // Assuming you have this
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.DispatchAssignedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class DispatchAssignedConsumer {

    private final CaseService caseService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    private static final String CONSUMER_NAME = "case-service-dispatch-consumer";

    public DispatchAssignedConsumer(CaseService caseService,
                                    ProcessedEventRepository processedEventRepository,
                                    ObjectMapper objectMapper) {
        this.caseService = caseService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.DISPATCH_EVENTS, groupId = "case-service-group")
    @Transactional
    public void onDispatchAssigned(String jsonPayload) {
        try {
            // 1. Translate string to Java record
            DispatchAssignedEvent event = objectMapper.readValue(jsonPayload, DispatchAssignedEvent.class);

            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            if (processedEventRepository.existsById(id)) {
                System.out.println("⚠️ Duplicate event detected! Skipping DispatchAssignedEvent: " + eventIdString);
                return;
            }

            // 2. Your Case Service logic (e.g., creating the case in the DB)
            System.out.println("🗂️ Case Service received new DispatchAssigned for  " + event.emergencyId());
            caseService.onDispatchAssigned(event);

            // 3. Save receipt
            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process dispatch assignment: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
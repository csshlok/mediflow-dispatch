package org.example.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.notification.entity.ProcessedEvent;
import org.example.notification.entity.ProcessedEventId;
import org.example.notification.repository.ProcessedEventRepository;
import org.example.notification.service.NotificationService; // <-- ADDED IMPORT
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.PatientDeliveredEvent;
import org.example.shared.events.PatientPickedUpEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class PatientDispatchConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService; // <-- ADDED FIELD

    // Define a unique consumer name for the composite key
    private static final String CONSUMER_NAME = "notification-service-patient-dispatch-consumer";

    public PatientDispatchConsumer(ProcessedEventRepository processedEventRepository,
                                   ObjectMapper objectMapper,
                                   NotificationService notificationService) { // <-- ADDED CONSTRUCTOR PARAMETER
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService; // <-- ADDED ASSIGNMENT
    }

    @KafkaListener(topics = KafkaTopics.AMBULANCE_EVENTS, groupId = "notification-service-group")
    @Transactional
    public void notifyPickup(String jsonPayload) {
        try {
            JsonNode node = objectMapper.readTree(jsonPayload);
            if (!node.has("pickedUpAt")) {
                //System.out.println("Notification Service received non-pickup event on ambulance-events; skipping.");
                return;
            }

            // 1. TRANSLATE: Convert JSON to Java Record
            PatientPickedUpEvent event = objectMapper.treeToValue(node, PatientPickedUpEvent.class);

            String eventIdString = event.eventId().toString();
            ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

            // 🛡️ 2. Idempotency Check
            if (processedEventRepository.existsById(id)) {
                System.out.println("⚠️ Duplicate PatientPickedUp event detected! Skipping: " + eventIdString);
                return;
            }

            // 3. Execute Business Logic <-- CHANGED TO USE NOTIFICATION SERVICE
            notificationService.notify(
                    "Emergency Update: " + event.emergencyId(),
                    "Paramedics have secured the patient and are en route to the hospital.",
                    "Ambulance ID: " + event.ambulanceId()
            );

            // 4. Mark Event as Processed
            processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process pickup event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @KafkaListener(topics = KafkaTopics.HOSPITAL_EVENTS, groupId = "notification-service-group")
    @Transactional
    public void notifyDelivery(String jsonPayload) {
        try {
            // Some topics (e.g. "hospital-events") carry multiple event types (PatientDeliveredEvent, HospitalAssignedEvent).
            // We must detect the concrete event shape before deserializing to the specific record.
            JsonNode node = objectMapper.readTree(jsonPayload);

            if (node.has("deliveredAt")) {
                // It's a PatientDeliveredEvent
                PatientDeliveredEvent event = objectMapper.treeToValue(node, PatientDeliveredEvent.class);

                String eventIdString = event.eventId().toString();
                ProcessedEventId id = new ProcessedEventId(eventIdString, CONSUMER_NAME);

                // 🛡️ 2. Idempotency Check
                if (processedEventRepository.existsById(id)) {
                    System.out.println("⚠️ Duplicate PatientDelivered event detected! Skipping: " + eventIdString);
                    return;
                }

                // 3. Execute Business Logic
                notificationService.notify(
                        "Emergency Resolved: " + event.emergencyId(),
                        "The patient has safely arrived at Hospital " + event.hospitalId() + ". Emergency resolved.",
                        "Hospital ID: " + event.hospitalId()
                );

                // 4. Mark Event as Processed
                processedEventRepository.save(new ProcessedEvent(eventIdString, CONSUMER_NAME, Instant.now()));
            } else {
                // Unknown or not a PatientDeliveredEvent - ignore or log for now
                //System.out.println("ℹ️ Received non-delivery event on hospital-events topic; skipping. Raw: " + jsonPayload);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Failed to process delivery event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

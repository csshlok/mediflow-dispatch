package org.example.emergencyrequest.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.emergencyrequest.entity.EmergencyStatus;
import org.example.emergencyrequest.service.EmergencyStatusService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.DispatchAssignedEvent;
import org.example.shared.events.PatientDeliveredEvent;
import org.example.shared.events.PatientPickedUpEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Follows the dispatch lifecycle so GET /emergency/{id} reflects where the emergency actually is
@Component
public class EmergencyStatusConsumer {

    private static final String GROUP = "emergency-request-service-status";

    private final EmergencyStatusService statusService;
    private final ObjectMapper objectMapper;

    public EmergencyStatusConsumer(EmergencyStatusService statusService, ObjectMapper objectMapper) {
        this.statusService = statusService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.DISPATCH_EVENTS, groupId = GROUP)
    public void onDispatchAssigned(String jsonPayload) throws JsonProcessingException {
        DispatchAssignedEvent event = objectMapper.readValue(jsonPayload, DispatchAssignedEvent.class);
        statusService.advance(event.emergencyId(), EmergencyStatus.DISPATCHED, event.ambulanceId(), event.hospitalId());
    }

    @KafkaListener(topics = KafkaTopics.AMBULANCE_EVENTS, groupId = GROUP)
    public void onAmbulanceEvent(String jsonPayload) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonPayload);
        if (node.has("pickedUpAt")) {
            PatientPickedUpEvent event = objectMapper.treeToValue(node, PatientPickedUpEvent.class);
            statusService.advance(event.emergencyId(), EmergencyStatus.PATIENT_PICKED_UP, event.ambulanceId(), null);
        }
    }

    @KafkaListener(topics = KafkaTopics.HOSPITAL_EVENTS, groupId = GROUP)
    public void onHospitalEvent(String jsonPayload) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonPayload);
        if (node.has("deliveredAt")) {
            PatientDeliveredEvent event = objectMapper.treeToValue(node, PatientDeliveredEvent.class);
            statusService.advance(event.emergencyId(), EmergencyStatus.DELIVERED, null, event.hospitalId());
        }
    }

    // Matching parks emergencies here once its retries are exhausted
    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS + "-dlq", groupId = GROUP)
    public void onDispatchDeadLettered(String jsonPayload) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonPayload);
        if (node.hasNonNull("emergencyId")) {
            statusService.advance(UUID.fromString(node.get("emergencyId").asText()), EmergencyStatus.DISPATCH_FAILED, null, null);
        }
    }
}

package org.example.matching.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.matching.entity.ProcessedEventId;
import org.example.matching.repository.ProcessedEventRepository;
import org.example.matching.service.MatchingService;
import org.example.shared.config.KafkaTopics;
import org.example.shared.events.EmergencyRequestedEvent;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

@Component
public class EmergencyConsumer {

    private final MatchingService matchingService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    private static final String CONSUMER_NAME = "matching-service-emergency-consumer";

    public EmergencyConsumer(MatchingService matchingService,
                             ProcessedEventRepository processedEventRepository,
                             ObjectMapper objectMapper) {
        this.matchingService = matchingService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    // Failed dispatches are retried on delay topics (2s, 4s, ... then every 60s, about 5 minutes in total, which
    // outlasts the 2-minute stale-saga threshold) without blocking other emergencies, then parked on
    // emergency-events-dlq. Malformed payloads go straight to the DLQ.
    @RetryableTopic(
            attempts = "10",
            backOff = @BackOff(delay = 2000, multiplier = 2.0, maxDelay = 60000),
            dltTopicSuffix = "-dlq",
            exclude = JsonProcessingException.class)
    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS, groupId = "matching-service-group")
    public void onEmergencyRequested(String jsonPayload) throws JsonProcessingException {
        EmergencyRequestedEvent event = objectMapper.readValue(jsonPayload, EmergencyRequestedEvent.class);

        if (processedEventRepository.existsById(new ProcessedEventId(event.eventId().toString(), CONSUMER_NAME))) {
            return;
        }

        // Records the receipt itself, in the same transaction as the dispatch outcome
        matchingService.processEmergency(event, CONSUMER_NAME);
    }

    @DltHandler
    public void onDeadLetter(String jsonPayload) {
        System.err.println("☠️ Emergency could not be dispatched after all retries and needs manual attention: " + jsonPayload);
    }
}

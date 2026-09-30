package org.example.location.producer;

import org.example.shared.config.KafkaTopics;
import org.example.shared.events.AmbulanceLocationUpdatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LocationProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishLocationUpdate(AmbulanceLocationUpdatedEvent event) {
        kafkaTemplate.send(KafkaTopics.LOCATION_EVENTS, event.ambulanceId().toString(), event);
    }
}
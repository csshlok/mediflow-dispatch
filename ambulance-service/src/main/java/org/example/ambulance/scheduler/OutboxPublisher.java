package org.example.ambulance.scheduler;

import org.example.ambulance.entity.OutboxEvent;
import org.example.ambulance.repository.OutboxRepository;
import org.example.shared.config.KafkaTopics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;

    // 🛡️ UPGRADE 1: Strictly typed to <String, String> to prevent accidental JSON double-serialization
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter outboxEventsPublished;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventsPublished = Counter.builder("outbox_events_published")
                .description("Outbox events successfully published from the ambulance service")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5000)
    public void processOutbox() {
        // 🛡️ UPGRADE 2: Maintained your excellent chronological sorting to prevent race conditions
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                // Determine the correct topic based on the event type
                String topic = event.getEventType().equals("PatientDeliveredEvent")
                        ? KafkaTopics.HOSPITAL_EVENTS
                        : KafkaTopics.AMBULANCE_EVENTS;

                // 🛡️ UPGRADE 3: Added the Kafka Key (event.getAggregateId()) for partition ordering
                // 🛡️ UPGRADE 4: Added .get() to force the thread to wait for Kafka's explicit acknowledgment
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();

                // This code only executes if Kafka actually confirmed receipt
                event.setStatus("PUBLISHED");
                outboxRepository.save(event);
                outboxEventsPublished.increment();
                System.out.println("📤 Outbox Poller: Published " + event.getEventType());

            } catch (Exception e) {
                // If Kafka is down, the error is caught here.
                // The status remains 'PENDING', meaning the poller will automatically retry it in 5 seconds.
                System.err.println("❌ Outbox Poller: Failed to publish event " + event.getId() + " - " + e.getMessage());
            }
        }
    }
}

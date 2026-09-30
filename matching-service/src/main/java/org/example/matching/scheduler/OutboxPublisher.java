package org.example.matching.scheduler;

import org.example.matching.entity.OutboxEvent;
import org.example.matching.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate; // Note: We are sending JSON Strings now!
    private final Counter outboxEventsPublished;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventsPublished = Counter.builder("outbox_events_published")
                .description("Outbox events successfully published from the matching service")
                .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${medical.outbox.publish-rate-ms:200}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                // Send to Kafka using the Topic Name we stored in 'eventType'
                kafkaTemplate.send(event.getEventType(), event.getPayload()).get(); // .get() forces it to wait for success

                // If successful, mark as PUBLISHED
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                outboxEventsPublished.increment();

            } catch (Exception e) {
                System.err.println("⚠️ Outbox failed to publish for Emergency ID: " + event.getAggregateId());
            }

        }
    }
}

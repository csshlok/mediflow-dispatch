package org.example.case_.scheduler;

import org.example.case_.entity.OutboxEvent;
import org.example.case_.repository.OutboxRepository;
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter outboxEventsPublished;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventsPublished = Counter.builder("outbox_events_published")
                .description("Outbox events successfully published from the case service")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                // Send the JSON string to Kafka
                kafkaTemplate.send(event.getEventType(), event.getPayload()).get();

                // If successful, flip the status
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                outboxEventsPublished.increment();

                System.out.println("📤 Kafka Published [" + event.getEventType() + "] for Emergency ID: " + event.getAggregateId());
            } catch (Exception e) {
                System.err.println("⚠️ Outbox failed to publish for Emergency ID: " + event.getAggregateId());
            }
        }
    }
}

package org.example.emergencyrequest.scheduler;

import org.example.emergencyrequest.entity.OutboxEvent;
import org.example.emergencyrequest.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter outboxEventsPublished;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventsPublished = Counter.builder("outbox_events_published")
                .description("Outbox events successfully published from the emergency request service")
                .register(meterRegistry);
    }

    // One batch per run inside a transaction holding row locks. Events are keyed by emergency id so each
    // emergency's events stay ordered on one partition, and the batch stops at the first failure so a later
    // event never overtakes an earlier one that could not be sent.
    @Scheduled(fixedDelayString = "${medical.outbox.publish-rate-ms:200}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventsPublished.increment();

            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Stays PENDING and is retried on the next run
                System.err.println("⚠️ Outbox failed to publish " + event.getEventType() + " for Emergency ID: "
                        + event.getAggregateId() + " - " + e.getMessage());
                return;
            }
        }
    }
}

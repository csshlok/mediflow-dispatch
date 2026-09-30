package org.example.ambulance.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.ambulance.entity.OutboxEvent;
import org.example.ambulance.repository.OutboxRepository;
import org.example.shared.config.KafkaTopics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

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
                .description("Outbox events successfully published from the ambulance service")
                .register(meterRegistry);
    }

    // One batch per run inside a transaction holding row locks. Events are keyed by emergency id so each
    // emergency's events stay ordered on one partition, and the batch stops at the first failure so a later
    // event never overtakes an earlier one that could not be sent.
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                // Deliveries go to hospital-events, other lifecycle events to ambulance-events
                String topic = event.getEventType().equals("PatientDeliveredEvent")
                        ? KafkaTopics.HOSPITAL_EVENTS
                        : KafkaTopics.AMBULANCE_EVENTS;
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setStatus("PUBLISHED");
                outboxEventsPublished.increment();

            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Stays PENDING and is retried on the next run
                log.warn("⚠️ Outbox failed to publish " + event.getEventType() + " for Emergency ID: "
                        + event.getAggregateId() + " - " + e.getMessage());
                return;
            }
        }
    }
}

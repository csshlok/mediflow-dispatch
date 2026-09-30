package org.example.matching.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.example.shared.config.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

// Moves emergencies whose dispatch retries were exhausted back onto the main topic, e.g. after ambulances
// or hospital capacity became available again. Progress is tracked by a dedicated consumer group, so each
// dead-lettered record is re-driven once.
@Service
public class DeadLetterRedriveService {

    public static final String DLQ_TOPIC = KafkaTopics.EMERGENCY_EVENTS + "-dlq";
    private static final String REDRIVE_GROUP = "matching-service-dlq-redrive";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ASSIGNMENT_TIMEOUT = Duration.ofSeconds(20);

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRedriveService.class);

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DeadLetterRedriveService(ConsumerFactory<String, String> consumerFactory,
                                    KafkaTemplate<String, String> kafkaTemplate) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    public synchronized int redrive(int max) {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(Math.min(max, 500)));

        int moved = 0;
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(REDRIVE_GROUP, "redrive", null, overrides)) {
            consumer.subscribe(List.of(DLQ_TOPIC));
            long assignmentDeadline = System.nanoTime() + ASSIGNMENT_TIMEOUT.toNanos();

            while (moved < max) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    // An empty poll before partitions are assigned says nothing about the topic yet
                    if (consumer.assignment().isEmpty() && System.nanoTime() < assignmentDeadline) {
                        continue;
                    }
                    break;
                }
                for (ConsumerRecord<String, String> record : records) {
                    kafkaTemplate.send(KafkaTopics.EMERGENCY_EVENTS, record.key(), record.value()).get(10, TimeUnit.SECONDS);
                    moved++;
                }
                // Only commit once every record in the batch is safely back on the main topic
                consumer.commitSync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Re-drive interrupted after " + moved + " records", e);
        } catch (Exception e) {
            throw new IllegalStateException("Re-drive failed after " + moved + " records: " + e.getMessage(), e);
        }

        log.info("Re-drove {} dead-lettered emergencies back to {}", moved, KafkaTopics.EMERGENCY_EVENTS);
        return moved;
    }
}

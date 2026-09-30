package org.example.matching.infrastructure.kafka.dlq;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class DlqConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {

        // If a message fails permanently, append "-dlq" to its original topic name and send it there.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> {
                    System.err.println("☠️ POISON MESSAGE DETECTED! Routing to DLQ: " + record.topic() + "-dlq");
                    return new TopicPartition(record.topic() + "-dlq", record.partition());
                });

        // Try to process the message 5 times (with a 1-second pause between attempts) before calling the quarantine router.
        long retryIntervalMs = 1000L;
        long maxRetries = 5L;

        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, maxRetries));
    }
}
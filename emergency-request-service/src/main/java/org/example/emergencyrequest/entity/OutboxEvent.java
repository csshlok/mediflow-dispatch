package org.example.emergencyrequest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent() {}

    public OutboxEvent(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public void setStatus(String status) { this.status = status; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getAggregateId() {
        return aggregateId;
    }
}
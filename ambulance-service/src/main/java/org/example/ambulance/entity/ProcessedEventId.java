package org.example.ambulance.entity;

import java.io.Serializable;
import java.util.Objects;

public class ProcessedEventId implements Serializable {

    private String eventId;
    private String consumerName;

    // JPA requires a no-args constructor
    public ProcessedEventId() {}

    public ProcessedEventId(String eventId, String consumerName) {
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    // equals() and hashCode() are strictly required by JPA for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEventId that = (ProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) &&
                Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerName);
    }
}
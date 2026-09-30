package org.example.hospital.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotent_requests")
public class IdempotentRequest {

    @Id
    private String idempotencyKey;
    private String responsePayload;
    private int responseStatus;
    private Instant createdAt;

    public IdempotentRequest() {}

    public IdempotentRequest(String idempotencyKey, String responsePayload, int responseStatus) {
        this.idempotencyKey = idempotencyKey;
        this.responsePayload = responsePayload;
        this.responseStatus = responseStatus;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getResponsePayload() { return responsePayload; }
    public int getResponseStatus() { return responseStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
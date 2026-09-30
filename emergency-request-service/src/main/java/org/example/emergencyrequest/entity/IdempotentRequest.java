package org.example.emergencyrequest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotent_requests")
public class IdempotentRequest {

    @Id
    private String idempotencyKey;
    private String responsePayload; // Store the JSON response
    private int responseStatus;
    private Instant createdAt;

    // Constructors, Getters, and Setters
    public IdempotentRequest() {
    }

    public IdempotentRequest(String idempotencyKey, String responsePayload, int responseStatus) {
        this.idempotencyKey = idempotencyKey;
        this.responsePayload = responsePayload;
        this.responseStatus = responseStatus;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
}
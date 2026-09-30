package org.example.location.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.Instant;
import java.util.UUID;

@RedisHash("AmbulanceLocation")
public class AmbulanceLocation {

    @Id
    private UUID ambulanceId; // Using ambulance ID as the primary key here

    private double latitude;
    private double longitude;
    private Instant updatedAt;

    public AmbulanceLocation() {}

    public AmbulanceLocation(UUID ambulanceId, double latitude, double longitude, Instant updatedAt) {
        this.ambulanceId = ambulanceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(UUID ambulanceId) { this.ambulanceId = ambulanceId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

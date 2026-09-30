package org.example.emergencyrequest.entity;

import jakarta.persistence.*;
import org.example.shared.enums.Severity;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emergency_requests")
public class EmergencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String patientId;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private double latitude;
    private double longitude;

    private String status;

    private UUID ambulanceId;
    private UUID hospitalId;
    private Instant updatedAt;

    // Required by JPA
    public EmergencyRequest() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(UUID ambulanceId) { this.ambulanceId = ambulanceId; }

    public UUID getHospitalId() { return hospitalId; }
    public void setHospitalId(UUID hospitalId) { this.hospitalId = hospitalId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

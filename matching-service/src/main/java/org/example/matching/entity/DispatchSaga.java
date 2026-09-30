package org.example.matching.entity;

import jakarta.persistence.*;
import org.example.shared.enums.SagaState;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_saga")
public class DispatchSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sagaId;

    @Column(name = "emergency_id", nullable = false, unique = true, length = 36)
    private String emergencyId;

    @Column(name = "ambulance_id", length = 36)
    private String ambulanceId;

    @Column(name = "hospital_id", length = 36)
    private String hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SagaState state = SagaState.STARTED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DispatchSaga() {}

    public DispatchSaga(String emergencyId) {
        this.emergencyId = emergencyId;
    }

    // Getters and Setters
    public UUID getSagaId() { return sagaId; }
    public String getEmergencyId() { return emergencyId; }
    public String getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(String ambulanceId) { this.ambulanceId = ambulanceId; }
    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }
    public SagaState getState() { return state; }
    public void setState(SagaState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }
}
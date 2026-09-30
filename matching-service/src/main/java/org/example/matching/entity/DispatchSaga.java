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

    // Stops the recovery job and a live dispatch attempt from overwriting each other
    @Version
    private Long version;

    @Column(name = "emergency_id", nullable = false, unique = true, length = 36)
    private String emergencyId;

    // Set before the reserve call is made, so a crash mid-call still knows what to release
    @Column(name = "ambulance_id", length = 36)
    private String ambulanceId;

    @Column(name = "hospital_id", length = 36)
    private String hospitalId;

    @Column(name = "hospital_reservation_key", length = 200)
    private String hospitalReservationKey;

    // Incremented on every (re)start so each attempt uses fresh reservation keys
    @Column(name = "attempt", nullable = false)
    private int attempt;

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

    public void startNewAttempt() {
        this.attempt++;
        clearReservations();
        setState(SagaState.STARTED);
    }

    public void clearReservations() {
        this.ambulanceId = null;
        this.hospitalId = null;
        this.hospitalReservationKey = null;
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getSagaId() { return sagaId; }
    public String getEmergencyId() { return emergencyId; }
    public String getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(String ambulanceId) {
        this.ambulanceId = ambulanceId;
        this.updatedAt = Instant.now();
    }
    public String getHospitalId() { return hospitalId; }
    public String getHospitalReservationKey() { return hospitalReservationKey; }
    public void setHospitalReservation(String hospitalId, String reservationKey) {
        this.hospitalId = hospitalId;
        this.hospitalReservationKey = reservationKey;
        this.updatedAt = Instant.now();
    }
    public int getAttempt() { return attempt; }
    public SagaState getState() { return state; }
    public void setState(SagaState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }
    public Instant getUpdatedAt() { return updatedAt; }
}

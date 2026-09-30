package org.example.hospital.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// One row per reservation key, so reserve and release are both idempotent and a bed is never returned twice
@Entity
@Table(name = "bed_reservations")
public class BedReservation {

    // DISCHARGED: the patient left and the bed was returned; RELEASED: the saga undid the reservation
    public enum Status { RESERVED, RELEASED, DISCHARGED }

    @Id
    @Column(name = "reservation_key", length = 200)
    private String reservationKey;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    // The emergency the bed is held for, so it can be discharged later
    @Column(name = "emergency_id")
    private UUID emergencyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BedReservation() {}

    public BedReservation(String reservationKey, UUID hospitalId, UUID emergencyId, Status status) {
        this.reservationKey = reservationKey;
        this.hospitalId = hospitalId;
        this.emergencyId = emergencyId;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getReservationKey() { return reservationKey; }
    public UUID getHospitalId() { return hospitalId; }
    public UUID getEmergencyId() { return emergencyId; }
    public Status getStatus() { return status; }

    public void markDischarged() {
        this.status = Status.DISCHARGED;
        this.updatedAt = Instant.now();
    }

    public void markReleased() {
        this.status = Status.RELEASED;
        this.updatedAt = Instant.now();
    }
}

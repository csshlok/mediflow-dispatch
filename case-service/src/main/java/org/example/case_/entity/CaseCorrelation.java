package org.example.case_.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_correlations")
public class CaseCorrelation {

    @Id
    private UUID emergencyId;

    private UUID ambulanceId;
    private UUID hospitalId;
    private String hospitalName;
    private UUID caseId;
    private boolean caseCreated;
    private Instant createdAt;
    private Instant updatedAt;

    public CaseCorrelation() {}

    public CaseCorrelation(UUID emergencyId, Instant now) {
        this.emergencyId = emergencyId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getEmergencyId() {
        return emergencyId;
    }

    public void setEmergencyId(UUID emergencyId) {
        this.emergencyId = emergencyId;
    }

    public UUID getAmbulanceId() {
        return ambulanceId;
    }

    public void setAmbulanceId(UUID ambulanceId) {
        this.ambulanceId = ambulanceId;
    }

    public UUID getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(UUID hospitalId) {
        this.hospitalId = hospitalId;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public boolean isCaseCreated() {
        return caseCreated;
    }

    public void setCaseCreated(boolean caseCreated) {
        this.caseCreated = caseCreated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

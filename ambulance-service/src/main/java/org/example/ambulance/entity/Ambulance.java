package org.example.ambulance.entity;

import jakarta.persistence.*;
import org.example.shared.enums.AmbulanceStatus;
import java.util.UUID;

@Entity
@Table(name = "ambulances")
public class Ambulance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 🛡️ The Optimistic Lock
    @Version
    private Long version;

    private String registrationNumber;
    private String capabilities; // e.g., "ALS, Ventilator, Defibrillator"
    private String crewInfo;     // e.g., "2 Paramedics, 1 Driver"

    @Enumerated(EnumType.STRING)
    private AmbulanceStatus status;

    // Default Constructor for JPA
    public Ambulance() {}

    public Ambulance(String registrationNumber, String capabilities, String crewInfo, AmbulanceStatus status) {
        this.registrationNumber = registrationNumber;
        this.capabilities = capabilities;
        this.crewInfo = crewInfo;
        this.status = status;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getCrewInfo() { return crewInfo; }
    public void setCrewInfo(String crewInfo) { this.crewInfo = crewInfo; }
    public AmbulanceStatus getStatus() { return status; }
    public void setStatus(AmbulanceStatus status) { this.status = status; }
}
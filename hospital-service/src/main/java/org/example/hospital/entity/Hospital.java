package org.example.hospital.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@Entity
@Table(name = "hospitals")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonProperty("hospitalName")
    private String name;
    private Integer availableBeds;

    @Version
    private Long version;

    private double latitude;
    private double longitude;

    public Hospital() {}

    public Hospital(String name, Integer availableBeds, double latitude, double longitude) {
        this.name = name;
        this.availableBeds = availableBeds;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAvailableBeds() { return availableBeds; }
    public void setAvailableBeds(Integer availableBeds) { this.availableBeds = availableBeds; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
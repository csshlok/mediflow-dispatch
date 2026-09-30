package org.example.location.service;

import org.example.location.entity.AmbulanceLocation;
import org.example.location.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository repository;
    public LocationService(LocationRepository repository) {
        this.repository = repository;
    }

    public AmbulanceLocation updateLocation(UUID ambulanceId, double latitude, double longitude) {
        AmbulanceLocation location = repository.findById(ambulanceId)
                .orElse(new AmbulanceLocation(ambulanceId, latitude, longitude, Instant.now()));
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setUpdatedAt(Instant.now());

        return repository.save(location);
    }

    public Iterable<AmbulanceLocation> getAllLocations() {
        return repository.findAll();
    }
}

package org.example.location.controller;

import org.example.location.entity.AmbulanceLocation;
import org.example.location.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    // Get all tracked ambulance locations
    @GetMapping
    public ResponseEntity<Iterable<AmbulanceLocation>> getAllLocations() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    // (Optional) Get a specific ambulance's location for debugging
    @GetMapping("/{ambulanceId}")
    public ResponseEntity<AmbulanceLocation> getLocationById(@PathVariable UUID ambulanceId) {
        // Since we didn't add a specific findById in the service layer yet,
        // we can filter the iterable for a quick debug endpoint.
        for (AmbulanceLocation loc : locationService.getAllLocations()) {
            if (loc.getAmbulanceId().equals(ambulanceId)) {
                return ResponseEntity.ok(loc);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
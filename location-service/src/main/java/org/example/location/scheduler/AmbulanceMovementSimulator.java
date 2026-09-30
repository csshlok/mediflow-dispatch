package org.example.location.scheduler;

import org.example.location.entity.AmbulanceLocation;
import org.example.location.producer.LocationProducer;
import org.example.location.service.LocationService;
import org.example.shared.enums.AmbulanceStatus;
import org.example.shared.events.AmbulanceLocationUpdatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "location.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class AmbulanceMovementSimulator {

    private final LocationService locationService;
    private final LocationProducer producer;
    private final RestTemplate restTemplate;
    private final String ambulanceServiceUrl;

    private final Map<UUID, double[]> locationCache = new ConcurrentHashMap<>();

    public AmbulanceMovementSimulator(LocationService locationService,
                                      LocationProducer producer,
                                      RestTemplate restTemplate,
                                      @Value("${ambulance.service.url:http://localhost:8083}") String ambulanceServiceUrl) {
        this.locationService = locationService;
        this.producer = producer;
        this.restTemplate = restTemplate;
        this.ambulanceServiceUrl = ambulanceServiceUrl;
    }

    @Scheduled(fixedRate = 5000)
    public void simulateMovement() {
        try {
            // 1. Fetch currently AVAILABLE ambulances
            String url = ambulanceServiceUrl + "/ambulances?status=" + AmbulanceStatus.AVAILABLE;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> ambulances = response.getBody();
            if (ambulances == null) return;

            for (Map<String, Object> amb : ambulances) {
                try {
                    UUID id = UUID.fromString(String.valueOf(amb.get("id")));

                    locationCache.putIfAbsent(id, new double[]{28.61 + Math.random() * 0.05, 77.20 + Math.random() * 0.05});

                    double[] coords = locationCache.get(id);

                    double newLat = coords[0] + ((Math.random() - 0.5) * 0.004);
                    double newLon = coords[1] + ((Math.random() - 0.5) * 0.004);
                    locationCache.put(id, new double[]{newLat, newLon});

                    locationService.updateLocation(id, newLat, newLon);

                } catch (Exception e) {
                    System.err.println("Failed to simulate location for ambulance " + amb.get("id") + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Simulator waiting for Ambulance Service: " + e.getMessage());
        }
    }
}

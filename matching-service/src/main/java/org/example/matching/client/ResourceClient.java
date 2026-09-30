package org.example.matching.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ResourceClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    private final RestTemplate restTemplate;

    @Value("${services.ambulance-url}")
    private String ambulanceUrl;

    @Value("${services.location-url}")
    private String locationUrl;

    @Value("${services.hospital-url}")
    private String hospitalUrl;

    public ResourceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Retries only transient failures (I/O errors, timeouts, 5xx). A 4xx is an answer, not an outage.
    private <T> T executeWithRetry(Supplier<T> networkCall, String operationName) {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; ; attempt++) {
            try {
                return networkCall.get();
            } catch (ResourceAccessException | HttpServerErrorException e) {
                if (attempt == MAX_ATTEMPTS) {
                    System.err.println("❌ [" + operationName + "] Failed after " + MAX_ATTEMPTS + " attempts: " + e.getMessage());
                    throw e;
                }

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry sleep interrupted", ie);
                }

                backoffMs *= 2;
            }
        }
    }

    public List<Map<String, Object>> fetchAvailableAmbulances(String status) {
        return executeWithRetry(() -> restTemplate.exchange(
                ambulanceUrl + "/ambulances?status=" + status,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        ).getBody(), "Fetch Ambulances");
    }

    public List<Map<String, Object>> fetchLocations() {
        return executeWithRetry(() -> restTemplate.exchange(
                locationUrl + "/locations",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        ).getBody(), "Fetch Locations");
    }

    public List<Map<String, Object>> fetchHospitals(int minBeds) {
        return executeWithRetry(() -> restTemplate.exchange(
                hospitalUrl + "/hospitals?minBeds=" + minBeds,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        ).getBody(), "Fetch Hospitals");
    }

    // Returns false when the ambulance was taken by another emergency (or no longer exists)
    public boolean reserveAmbulance(UUID ambulanceId, UUID emergencyId) {
        try {
            executeWithRetry(() -> restTemplate.postForObject(
                    ambulanceUrl + "/ambulances/" + ambulanceId + "/reserve",
                    Map.of("emergencyId", emergencyId.toString()),
                    Void.class
            ), "Reserve Ambulance");
            return true;
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    // Safe to repeat: only undoes a reservation still held by this emergency
    public void releaseAmbulance(UUID ambulanceId, UUID emergencyId) {
        try {
            executeWithRetry(() -> restTemplate.postForObject(
                    ambulanceUrl + "/ambulances/" + ambulanceId + "/release",
                    Map.of("emergencyId", emergencyId.toString()),
                    Void.class
            ), "Release Ambulance");
        } catch (HttpClientErrorException.NotFound e) {
            // Ambulance deleted; nothing left to release
        }
    }

    // Returns false when the hospital has no free bed (or no longer exists)
    public boolean reserveHospitalBed(UUID hospitalId, String reservationKey) {
        try {
            executeWithRetry(() -> restTemplate.exchange(
                    hospitalUrl + "/hospitals/" + hospitalId + "/reserve-bed",
                    HttpMethod.PATCH,
                    new HttpEntity<>(keyHeader(reservationKey)),
                    String.class
            ), "Reserve Hospital");
            return true;
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    // Safe to repeat: the hospital returns the bed for this key at most once
    public void releaseHospitalBed(UUID hospitalId, String reservationKey) {
        executeWithRetry(() -> restTemplate.exchange(
                hospitalUrl + "/hospitals/" + hospitalId + "/release-bed",
                HttpMethod.POST,
                new HttpEntity<>(keyHeader(reservationKey)),
                String.class
        ), "Release Hospital");
    }

    private static HttpHeaders keyHeader(String reservationKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", reservationKey);
        return headers;
    }
}

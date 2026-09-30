package org.example.matching.service;

import org.example.matching.client.ResourceClient;
import org.example.matching.entity.DispatchSaga;
import org.example.matching.entity.ProcessedEvent;
import org.example.matching.producer.DispatchProducer;
import org.example.matching.repository.ProcessedEventRepository;
import org.example.matching.repository.SagaRepository;
import org.example.shared.enums.AmbulanceStatus;
import org.example.shared.enums.SagaState;
import org.example.shared.events.DispatchAssignedEvent;
import org.example.shared.events.EmergencyRequestedEvent;
import org.example.shared.events.HospitalAssignedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Runs the dispatch saga. There is deliberately no surrounding transaction: every saga step is
// committed as it happens so a crash leaves an accurate record of what must be released.
@Service
public class MatchingService {

    private static final EnumSet<SagaState> IN_PROGRESS_STATES = EnumSet.of(
            SagaState.STARTED, SagaState.AMBULANCE_RESERVED, SagaState.HOSPITAL_RESERVED);
    private static final EnumSet<SagaState> RECOVERABLE_STATES = EnumSet.of(
            SagaState.STARTED, SagaState.AMBULANCE_RESERVED, SagaState.HOSPITAL_RESERVED, SagaState.FAILED);

    private final ResourceClient resourceClient;
    private final DispatchProducer producer;
    private final SagaRepository sagaRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration staleAfter;
    private final Timer dispatchLatency;
    private final Counter sagaStarted;
    private final Counter sagaCompleted;
    private final Counter sagaCompensated;
    private final Counter sagaFailed;

    public MatchingService(ResourceClient resourceClient,
                           DispatchProducer producer,
                           SagaRepository sagaRepository,
                           ProcessedEventRepository processedEventRepository,
                           TransactionTemplate transactionTemplate,
                           @Value("${medical.matching.saga-stale-after:2m}") Duration staleAfter,
                           MeterRegistry meterRegistry) {
        this.resourceClient = resourceClient;
        this.producer = producer;
        this.sagaRepository = sagaRepository;
        this.processedEventRepository = processedEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.staleAfter = staleAfter;
        this.dispatchLatency = Timer.builder("dispatch_latency")
                .description("Time from emergency request creation to dispatch assignment")
                .publishPercentiles(0.95)
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.sagaStarted = meterRegistry.counter("saga_started");
        this.sagaCompleted = meterRegistry.counter("saga_completed");
        this.sagaCompensated = meterRegistry.counter("saga_compensated");
        this.sagaFailed = meterRegistry.counter("saga_failed");
    }

    // Dispatches one emergency. Throws DispatchFailedException after compensating, so the caller retries.
    public void processEmergency(EmergencyRequestedEvent event, String consumerName) {
        UUID emergencyId = event.emergencyId();
        DispatchSaga saga = sagaRepository.findByEmergencyId(emergencyId.toString()).orElse(null);

        if (saga != null && saga.getState() == SagaState.COMPLETED) {
            return; // already dispatched by an earlier delivery of this event
        }

        if (saga != null && IN_PROGRESS_STATES.contains(saga.getState()) && !isStale(saga)) {
            // Another consumer is dispatching this emergency right now (duplicate delivery); check back later
            throw new DispatchFailedException("Dispatch already in progress for emergency " + emergencyId, null);
        }

        if (saga == null) {
            saga = new DispatchSaga(emergencyId.toString());
        } else {
            // A previous attempt may have crashed holding reservations; undo them before starting over
            saga = compensate(saga);
        }

        saga.startNewAttempt();
        saga = sagaRepository.save(saga);
        UUID sagaId = saga.getSagaId();
        sagaStarted.increment();

        try {
            saga = reserveAmbulance(saga, event);
            HospitalChoice hospital = reserveHospital(saga, event);
            saga = hospital.saga();
            complete(saga, event, hospital.name(), consumerName);
            sagaCompleted.increment();
            if (event.createdAt() != null) {
                dispatchLatency.record(Duration.between(event.createdAt(), Instant.now()));
            }
        } catch (RuntimeException e) {
            System.err.println("Match failed for Emergency " + emergencyId + ": " + e.getMessage());
            // Reload: the database holds the latest recorded reservations, the local copy may be behind
            sagaRepository.findById(sagaId).ifPresent(this::compensateAfterFailure);
            throw new DispatchFailedException("Dispatch failed for emergency " + emergencyId, e);
        }
    }

    // Releases reservations of sagas that stopped making progress (e.g. the instance died mid-dispatch)
    public void recoverStaleSagas() {
        Instant cutoff = Instant.now().minus(staleAfter);
        for (DispatchSaga saga : sagaRepository.findByStateInAndUpdatedAtBefore(RECOVERABLE_STATES, cutoff)) {
            try {
                compensate(saga);
                sagaCompensated.increment();
                System.err.println("Recovered stale saga for emergency " + saga.getEmergencyId()
                        + "; it will be dispatched again when its event is retried (check the -dlq topic if retries are exhausted)");
            } catch (RuntimeException e) {
                System.err.println("Recovery of saga for emergency " + saga.getEmergencyId() + " failed, will retry: " + e.getMessage());
            }
        }
    }

    private DispatchSaga reserveAmbulance(DispatchSaga saga, EmergencyRequestedEvent event) {
        UUID emergencyId = event.emergencyId();
        List<Map<String, Object>> ambulances = copyOf(resourceClient.fetchAvailableAmbulances(AmbulanceStatus.AVAILABLE.name()));
        List<Map<String, Object>> locations = resourceClient.fetchLocations();

        while (true) {
            UUID candidate = findClosestAmbulance(event.latitude(), event.longitude(), ambulances, locations);
            if (candidate == null) {
                throw new IllegalStateException("No available ambulances remaining");
            }

            // Record the intent first: if we crash after the reserve call, recovery knows what to release
            saga.setAmbulanceId(candidate.toString());
            saga = sagaRepository.save(saga);

            if (resourceClient.reserveAmbulance(candidate, emergencyId)) {
                saga.setState(SagaState.AMBULANCE_RESERVED);
                return sagaRepository.save(saga);
            }

            // Taken by another emergency in the meantime; try the next closest one
            saga.setAmbulanceId(null);
            saga = sagaRepository.save(saga);
            ambulances.removeIf(amb -> candidate.equals(readUuid(amb, "ambulanceId", "id")));
        }
    }

    private record HospitalChoice(DispatchSaga saga, String name) {}

    private HospitalChoice reserveHospital(DispatchSaga saga, EmergencyRequestedEvent event) {
        List<Map<String, Object>> hospitals = copyOf(resourceClient.fetchHospitals(1));

        while (true) {
            UUID candidate = findClosestHospital(event.latitude(), event.longitude(), hospitals);
            if (candidate == null) {
                throw new IllegalStateException("No hospitals with free beds");
            }

            String reservationKey = saga.getEmergencyId() + ":" + saga.getAttempt() + ":" + candidate;
            saga.setHospitalReservation(candidate.toString(), reservationKey);
            saga = sagaRepository.save(saga);

            if (resourceClient.reserveHospitalBed(candidate, reservationKey)) {
                saga.setState(SagaState.HOSPITAL_RESERVED);
                return new HospitalChoice(sagaRepository.save(saga), findHospitalName(candidate, hospitals));
            }

            // Full since we fetched the list; try the next closest hospital
            saga.setHospitalReservation(null, null);
            saga = sagaRepository.save(saga);
            hospitals.removeIf(hospital -> candidate.equals(readUuid(hospital, "hospitalId", "id")));
        }
    }

    // Outbox events, the COMPLETED state and the processed-event receipt commit together or not at all
    private void complete(DispatchSaga saga, EmergencyRequestedEvent event, String hospitalName, String consumerName) {
        UUID emergencyId = event.emergencyId();
        UUID ambulanceId = UUID.fromString(saga.getAmbulanceId());
        UUID hospitalId = UUID.fromString(saga.getHospitalId());

        transactionTemplate.executeWithoutResult(status -> {
            producer.publishHospitalAssigned(new HospitalAssignedEvent(
                    UUID.randomUUID(), Instant.now(), emergencyId, hospitalId, hospitalName));
            producer.publishDispatch(new DispatchAssignedEvent(
                    UUID.randomUUID(), Instant.now(), emergencyId, ambulanceId, hospitalId));

            saga.setState(SagaState.COMPLETED);
            sagaRepository.save(saga);
            processedEventRepository.save(new ProcessedEvent(event.eventId().toString(), consumerName, Instant.now()));
        });
    }

    private void compensateAfterFailure(DispatchSaga saga) {
        try {
            compensate(saga);
            sagaCompensated.increment();
        } catch (RuntimeException ex) {
            System.err.println("CRITICAL: compensation failed for emergency " + saga.getEmergencyId()
                    + "; the recovery job will retry: " + ex.getMessage());
            try {
                saga.setState(SagaState.FAILED);
                sagaRepository.save(saga);
            } catch (RuntimeException saveEx) {
                System.err.println("Could not mark saga FAILED (it keeps its last recorded state): " + saveEx.getMessage());
            }
            sagaFailed.increment();
        }
    }

    // Releases everything the saga may hold. Both release calls are idempotent, so repeating this is safe.
    private DispatchSaga compensate(DispatchSaga saga) {
        if (saga.getHospitalReservationKey() != null) {
            resourceClient.releaseHospitalBed(UUID.fromString(saga.getHospitalId()), saga.getHospitalReservationKey());
        }
        if (saga.getAmbulanceId() != null) {
            resourceClient.releaseAmbulance(UUID.fromString(saga.getAmbulanceId()), UUID.fromString(saga.getEmergencyId()));
        }
        saga.clearReservations();
        saga.setState(SagaState.COMPENSATED);
        return sagaRepository.save(saga);
    }

    // Each saga step saves the saga, so no update for this long means the attempt is dead
    private boolean isStale(DispatchSaga saga) {
        return saga.getUpdatedAt().isBefore(Instant.now().minus(staleAfter));
    }

    private static List<Map<String, Object>> copyOf(List<Map<String, Object>> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private UUID findClosestAmbulance(
            double emergencyLat,
            double emergencyLon,
            List<Map<String, Object>> ambulances,
            List<Map<String, Object>> locations
    ) {
        if (ambulances == null || locations == null) {
            return null;
        }

        UUID closestId = null;
        double minDistance = Double.MAX_VALUE;
        Map<UUID, Map<String, Object>> locationByAmbulanceId = new HashMap<>();

        for (Map<String, Object> location : locations) {
            UUID id = readUuid(location, "ambulanceId", "id");
            if (id != null) {
                locationByAmbulanceId.put(id, location);
            }
        }

        for (Map<String, Object> ambulance : ambulances) {
            UUID id = readUuid(ambulance, "ambulanceId", "id");
            if (id == null) {
                continue;
            }

            Map<String, Object> location = locationByAmbulanceId.get(id);

            if (location != null) {
                double distance = calculateDistance(
                        emergencyLat,
                        emergencyLon,
                        readDouble(location, "latitude"),
                        readDouble(location, "longitude")
                );
                if (distance < minDistance) {
                    minDistance = distance;
                    closestId = id;
                }
            }
        }

        return closestId;
    }

    private UUID findClosestHospital(double emergencyLat, double emergencyLon, List<Map<String, Object>> hospitals) {
        if (hospitals == null) {
            return null;
        }

        UUID closestId = null;
        double minDistance = Double.MAX_VALUE;

        for (Map<String, Object> hospital : hospitals) {
            UUID id = readUuid(hospital, "hospitalId", "id");
            if (id == null) {
                continue;
            }

            double distance = calculateDistance(
                    // Keeping your exact calculations intact
                    emergencyLat,
                    emergencyLon,
                    readDouble(hospital, "latitude"),
                    readDouble(hospital, "longitude")
            );
            if (distance < minDistance) {
                minDistance = distance;
                closestId = id;
            }
        }

        return closestId;
    }

    private String findHospitalName(UUID hospitalId, List<Map<String, Object>> hospitals) {
        if (hospitals == null) {
            return "Assigned Hospital";
        }

        return hospitals.stream()
                .filter(hospital -> hospitalId.equals(readUuid(hospital, "hospitalId", "id")))
                .findFirst()
                .map(hospital -> readString(hospital, "hospitalName", readString(hospital, "name", "Assigned Hospital")))
                .orElse("Assigned Hospital");
    }

    private UUID readUuid(Map<String, Object> payload, String primaryKey, String fallbackKey) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(primaryKey);
        if (value == null && fallbackKey != null) {
            value = payload.get(fallbackKey);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private double readDouble(Map<String, Object> payload, String key) {
        if (payload == null) {
            return 0.0;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private String readString(Map<String, Object> payload, String key, String fallback) {
        if (payload == null) {
            return fallback;
        }
        Object value = payload.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}

package org.example.ambulance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.ambulance.entity.Ambulance;
import org.example.ambulance.entity.OutboxEvent;
import org.example.ambulance.repository.AmbulanceRepository;
import org.example.ambulance.repository.OutboxRepository;
import org.example.shared.enums.AmbulanceStatus;
import org.example.shared.events.PatientDeliveredEvent;
import org.example.shared.events.PatientPickedUpEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AmbulanceService {

    private final AmbulanceRepository repository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Counter outboxEventsCreated;

    public AmbulanceService(AmbulanceRepository repository,
                            OutboxRepository outboxRepository,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.outboxEventsCreated = Counter.builder("outbox_events_stored")
                .description("Outbox events written to the ambulance service database")
                .register(meterRegistry);
    }

    public List<Ambulance> getByStatus(AmbulanceStatus status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findByStatus(status);
    }

    // Called by the matching saga. Safe to retry: re-reserving for the same emergency succeeds.
    @Transactional
    public void reserve(UUID id, UUID emergencyId) {
        if (repository.reserveIfAvailable(id, emergencyId) == 1) {
            return;
        }
        Ambulance ambulance = find(id);
        if (ambulance.getStatus() == AmbulanceStatus.RESERVED && emergencyId.equals(ambulance.getAssignedEmergencyId())) {
            return;
        }
        throw new AmbulanceStateException("Ambulance " + id + " is " + ambulance.getStatus() + " and cannot be reserved");
    }

    // Saga compensation. Only undoes a reservation still held by this emergency; otherwise there is nothing to undo.
    @Transactional
    public void release(UUID id, UUID emergencyId) {
        if (repository.transitionHeldBy(id, emergencyId, AmbulanceStatus.RESERVED, AmbulanceStatus.AVAILABLE, null) == 0) {
            find(id);
        }
    }

    // Paramedic taps "Patient Secured"
    @Transactional
    public void registerPickup(UUID id, UUID emergencyId) {
        if (repository.transitionHeldBy(id, emergencyId, AmbulanceStatus.RESERVED, AmbulanceStatus.IN_TRANSIT, emergencyId) == 0) {
            Ambulance ambulance = find(id);
            if (ambulance.getStatus() == AmbulanceStatus.IN_TRANSIT && emergencyId.equals(ambulance.getAssignedEmergencyId())) {
                return; // repeated tap, event already recorded
            }
            throw new AmbulanceStateException("Ambulance " + id + " is not reserved for emergency " + emergencyId);
        }

        saveOutbox(emergencyId, "PatientPickedUpEvent",
                new PatientPickedUpEvent(UUID.randomUUID(), Instant.now(), emergencyId, id));
    }

    // Paramedic taps "Patient Delivered"
    @Transactional
    public void registerDelivery(UUID id, UUID emergencyId, UUID hospitalId) {
        if (repository.transitionHeldBy(id, emergencyId, AmbulanceStatus.IN_TRANSIT, AmbulanceStatus.AVAILABLE, null) == 0) {
            find(id);
            throw new AmbulanceStateException("Ambulance " + id + " is not transporting a patient for emergency " + emergencyId);
        }

        saveOutbox(emergencyId, "PatientDeliveredEvent",
                new PatientDeliveredEvent(UUID.randomUUID(), Instant.now(), emergencyId, hospitalId));
    }

    // Manual admin override, e.g. taking an ambulance out of service
    @Transactional
    public void setStatus(UUID id, AmbulanceStatus newStatus) {
        if (newStatus != AmbulanceStatus.AVAILABLE && newStatus != AmbulanceStatus.OFFLINE) {
            throw new IllegalArgumentException("Only AVAILABLE or OFFLINE can be set directly; use reserve/pickup/deliver");
        }
        Ambulance ambulance = find(id);
        ambulance.setStatus(newStatus);
        ambulance.setAssignedEmergencyId(null);
        repository.save(ambulance);
    }

    public Ambulance registerAmbulance(Ambulance request) {
        if (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank()) {
            throw new IllegalArgumentException("registrationNumber is required");
        }

        // Don't save if the registration number already exists
        if (repository.findByRegistrationNumber(request.getRegistrationNumber()).isPresent()) {
            throw new IllegalStateException("Ambulance with this registration number already exists");
        }

        // Build a fresh entity so client-supplied ids, versions or assignments are ignored
        Ambulance ambulance = new Ambulance(
                request.getRegistrationNumber(),
                request.getCapabilities() == null ? "" : request.getCapabilities(),
                request.getCrewInfo() == null ? "" : request.getCrewInfo(),
                AmbulanceStatus.AVAILABLE);
        try {
            return repository.save(ambulance);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Ambulance with this registration number already exists");
        }
    }

    private Ambulance find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AmbulanceNotFoundException(id));
    }

    private void saveOutbox(UUID emergencyId, String eventType, Object event) {
        try {
            outboxRepository.save(new OutboxEvent(emergencyId.toString(), eventType, objectMapper.writeValueAsString(event)));
            outboxEventsCreated.increment();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType + " for outbox", e);
        }
    }
}

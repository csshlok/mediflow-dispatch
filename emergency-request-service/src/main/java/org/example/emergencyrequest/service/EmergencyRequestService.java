package org.example.emergencyrequest.service;

import org.example.emergencyrequest.entity.EmergencyRequest;
import org.example.emergencyrequest.entity.EmergencyStatus;
import org.example.emergencyrequest.entity.IdempotentRequest;
import org.example.emergencyrequest.producer.EmergencyRequestProducer;
import org.example.emergencyrequest.repository.EmergencyRequestRepository;
import org.example.emergencyrequest.repository.IdempotentRequestRepository;
import org.example.shared.dto.EmergencyRequestDTO;
import org.example.shared.events.EmergencyRequestedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmergencyRequestService {

    private final EmergencyRequestRepository repository;
    private final IdempotentRequestRepository idempotencyRepository;
    private final EmergencyRequestProducer producer;
    private final Counter emergencyRequestsCounter;

    public EmergencyRequestService(EmergencyRequestRepository repository,
                                   IdempotentRequestRepository idempotencyRepository,
                                   EmergencyRequestProducer producer,
                                   MeterRegistry meterRegistry) {
        this.repository = repository;
        this.idempotencyRepository = idempotencyRepository;
        this.producer = producer;
        this.emergencyRequestsCounter = Counter.builder("emergency_requests")
                .description("Total emergency requests accepted by the emergency request service")
                .register(meterRegistry);
    }

    public Optional<IdempotentRequest> findPreviousResponse(String idempotencyKey) {
        return idempotencyRepository.findById(idempotencyKey);
    }

    // The emergency, its outbox event and the stored idempotent response commit together. If two requests race
    // with the same key, the primary key on idempotent_requests makes the second transaction roll back entirely.
    @Transactional
    public IdempotentRequest submit(String idempotencyKey, EmergencyRequestDTO requestDTO) {
        Optional<IdempotentRequest> existing = idempotencyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        validate(requestDTO);
        UUID generatedId = processEmergency(requestDTO);
        String successMessage = "Emergency Request successfully received and registered. Emergency ID: " + generatedId;

        return idempotencyRepository.save(new IdempotentRequest(idempotencyKey, successMessage, HttpStatus.OK.value()));
    }

    private UUID processEmergency(EmergencyRequestDTO requestDTO) {
        EmergencyRequest entity = new EmergencyRequest();
        entity.setPatientId(requestDTO.patientId());
        entity.setSeverity(requestDTO.severity());
        entity.setLatitude(requestDTO.latitude());
        entity.setLongitude(requestDTO.longitude());
        entity.setStatus(EmergencyStatus.PENDING_MATCH.name());
        entity.setUpdatedAt(Instant.now());

        EmergencyRequest savedEntity = repository.save(entity);

        EmergencyRequestedEvent event = new EmergencyRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                savedEntity.getId(),
                requestDTO.patientId(),
                requestDTO.severity(),
                requestDTO.latitude(),
                requestDTO.longitude()
        );

        producer.publishEvent(event);
        emergencyRequestsCounter.increment();

        return savedEntity.getId();
    }

    private static void validate(EmergencyRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (dto.patientId() == null || dto.patientId().isBlank()) {
            throw new IllegalArgumentException("patientId is required");
        }
        if (dto.severity() == null) {
            throw new IllegalArgumentException("severity is required (LOW, MEDIUM, HIGH or CRITICAL)");
        }
        if (dto.latitude() < -90 || dto.latitude() > 90 || dto.longitude() < -180 || dto.longitude() > 180) {
            throw new IllegalArgumentException("latitude/longitude are out of range");
        }
    }
}

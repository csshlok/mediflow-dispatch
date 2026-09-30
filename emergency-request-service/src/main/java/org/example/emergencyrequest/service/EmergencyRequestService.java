package org.example.emergencyrequest.service;

import org.example.emergencyrequest.entity.EmergencyRequest;
import org.example.emergencyrequest.producer.EmergencyRequestProducer;
import org.example.emergencyrequest.repository.EmergencyRequestRepository;
import org.example.shared.dto.EmergencyRequestDTO;
import org.example.shared.events.EmergencyRequestedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EmergencyRequestService {

    private final EmergencyRequestRepository repository;
    private final EmergencyRequestProducer producer;
    private final Counter emergencyRequestsCounter;

    public EmergencyRequestService(EmergencyRequestRepository repository,
                                   EmergencyRequestProducer producer,
                                   MeterRegistry meterRegistry) {
        this.repository = repository;
        this.producer = producer;
        this.emergencyRequestsCounter = Counter.builder("emergency_requests")
                .description("Total emergency requests accepted by the emergency request service")
                .register(meterRegistry);
    }

    @Transactional
    public UUID processEmergency(EmergencyRequestDTO requestDTO) {
        EmergencyRequest entity = new EmergencyRequest();
        entity.setPatientId(requestDTO.patientId());
        entity.setSeverity(requestDTO.severity());
        entity.setLatitude(requestDTO.latitude());
        entity.setLongitude(requestDTO.longitude());
        entity.setStatus("PENDING_MATCH");

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
}

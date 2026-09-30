package org.example.emergencyrequest.service;

import org.example.emergencyrequest.entity.EmergencyRequest;
import org.example.emergencyrequest.entity.EmergencyStatus;
import org.example.emergencyrequest.repository.EmergencyRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmergencyStatusService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyStatusService.class);

    private final EmergencyRequestRepository repository;

    public EmergencyStatusService(EmergencyRequestRepository repository) {
        this.repository = repository;
    }

    // Moves the emergency forward only; a stale or replayed event never moves it back
    @Transactional
    public void advance(UUID emergencyId, EmergencyStatus newStatus, UUID ambulanceId, UUID hospitalId) {
        Optional<EmergencyRequest> found = repository.findByIdForUpdate(emergencyId);
        if (found.isEmpty()) {
            log.warn("Status event for unknown emergency {} ({}); ignoring", emergencyId, newStatus);
            return;
        }

        EmergencyRequest emergency = found.get();
        EmergencyStatus current = EmergencyStatus.valueOf(emergency.getStatus());
        if (!newStatus.isAfter(current)) {
            return;
        }

        emergency.setStatus(newStatus.name());
        if (ambulanceId != null) {
            emergency.setAmbulanceId(ambulanceId);
        }
        if (hospitalId != null) {
            emergency.setHospitalId(hospitalId);
        }
        emergency.setUpdatedAt(Instant.now());
        log.info("Emergency {} moved {} -> {}", emergencyId, current, newStatus);
    }

    public Optional<EmergencyRequest> find(UUID emergencyId) {
        return repository.findById(emergencyId);
    }

    public List<EmergencyRequest> list(EmergencyStatus status) {
        return status == null ? repository.findAll() : repository.findByStatus(status.name());
    }
}

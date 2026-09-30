package org.example.ambulance.service;

import org.example.ambulance.entity.Ambulance;
import org.example.ambulance.repository.AmbulanceRepository;
import org.example.shared.enums.AmbulanceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AmbulanceService {

    private final AmbulanceRepository repository;

    public AmbulanceService(AmbulanceRepository repository) {
        this.repository = repository;
    }

    public List<Ambulance> getByStatus(AmbulanceStatus status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findByStatus(status);
    }

    @Transactional
    public void updateStatus(UUID id, AmbulanceStatus newStatus) {
        Ambulance ambulance = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ambulance not found: " + id));
        ambulance.setStatus(newStatus);
        repository.save(ambulance);
    }

    public Ambulance registerAmbulance(Ambulance ambulance) {
        // Don't save if the registration number already exists
        if (repository.findByRegistrationNumber(ambulance.getRegistrationNumber()).isPresent()) {
            throw new IllegalStateException("Ambulance with this registration number already exists");
        }

        ambulance.setStatus(AmbulanceStatus.AVAILABLE);
        return repository.save(ambulance);
    }
}

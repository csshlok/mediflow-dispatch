package org.example.hospital.service;

import org.example.hospital.entity.Hospital;
import org.example.hospital.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class HospitalService {

    private final HospitalRepository repository;
    private final boolean forceReservationFailure;

    public HospitalService(HospitalRepository repository,
                           @Value("${FORCE_RESERVATION_FAILURE:false}") boolean forceReservationFailure) {
        this.repository = repository;
        this.forceReservationFailure = forceReservationFailure;
    }

    public List<Hospital> getAvailableHospitals(int minBeds) {
        return repository.findByAvailableBedsGreaterThan(minBeds - 1); // e.g., > 0
    }

    @Transactional
    public void reserveBed(UUID hospitalId) {
        if (forceReservationFailure) {
            throw new IllegalStateException("Forced reservation failure for saga recovery test");
        }

        Hospital hospital = repository.findById(hospitalId)
                .orElseThrow(() -> new RuntimeException("Hospital not found: " + hospitalId));

        if (hospital.getAvailableBeds() <= 0) {
            throw new IllegalStateException("No available beds at hospital: " + hospitalId);
        }

        hospital.setAvailableBeds(hospital.getAvailableBeds() - 1);
        repository.save(hospital);
    }

    public Hospital registerHospital(Hospital hospital) {
        // Direct save - the controller handles the idempotency logic
        return repository.save(hospital);
    }
}

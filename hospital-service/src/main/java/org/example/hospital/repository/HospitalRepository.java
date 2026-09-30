package org.example.hospital.repository;

import org.example.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
    List<Hospital> findByAvailableBedsGreaterThan(int minBeds);
}
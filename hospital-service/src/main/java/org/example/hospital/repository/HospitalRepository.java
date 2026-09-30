package org.example.hospital.repository;

import org.example.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
    List<Hospital> findByAvailableBedsGreaterThan(int minBeds);

    // Atomic decrement; returns 0 when the hospital has no free bed left
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Hospital h set h.availableBeds = h.availableBeds - 1, h.version = h.version + 1 where h.id = :id and h.availableBeds > 0")
    int takeBed(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Hospital h set h.availableBeds = h.availableBeds + 1, h.version = h.version + 1 where h.id = :id")
    int returnBed(@Param("id") UUID id);
}

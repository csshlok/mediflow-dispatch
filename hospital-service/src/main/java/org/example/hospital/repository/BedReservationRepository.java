package org.example.hospital.repository;

import jakarta.persistence.LockModeType;
import org.example.hospital.entity.BedReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BedReservationRepository extends JpaRepository<BedReservation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BedReservation r where r.reservationKey = :key")
    Optional<BedReservation> findByKeyForUpdate(@Param("key") String key);
}

package org.example.case_.repository;

import org.example.case_.entity.CaseCorrelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CaseCorrelationRepository extends JpaRepository<CaseCorrelation, UUID> {

    @Modifying
    @Query(value = """
            insert into case_correlations (emergency_id, created_at, updated_at, case_created)
            values (:emergencyId, :now, :now, false)
            on conflict (emergency_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("emergencyId") UUID emergencyId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select correlation from CaseCorrelation correlation where correlation.emergencyId = :emergencyId")
    Optional<CaseCorrelation> findByIdForUpdate(@Param("emergencyId") UUID emergencyId);
}

package org.example.hospital.service;

import org.example.hospital.entity.BedReservation;
import org.example.hospital.repository.BedReservationRepository;
import org.example.hospital.repository.HospitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HospitalServiceTest {

    private final UUID hospitalId = UUID.randomUUID();
    private final UUID emergencyId = UUID.randomUUID();
    private final String key = emergencyId + ":1:" + hospitalId;

    private final Map<String, BedReservation> reservations = new HashMap<>();
    private HospitalRepository hospitals;
    private HospitalService service;
    private int beds;

    @BeforeEach
    void setUp() {
        beds = 2;
        hospitals = mock(HospitalRepository.class);
        when(hospitals.existsById(hospitalId)).thenReturn(true);
        when(hospitals.takeBed(hospitalId)).thenAnswer(inv -> {
            if (beds == 0) {
                return 0;
            }
            beds--;
            return 1;
        });
        when(hospitals.returnBed(hospitalId)).thenAnswer(inv -> { beds++; return 1; });

        BedReservationRepository repo = mock(BedReservationRepository.class);
        when(repo.findByKeyForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(reservations.get((String) inv.getArgument(0))));
        when(repo.save(any(BedReservation.class))).thenAnswer(inv -> {
            BedReservation r = inv.getArgument(0);
            reservations.put(r.getReservationKey(), r);
            return r;
        });
        when(repo.findForEmergencyForUpdate(any(), any())).thenAnswer(inv -> reservations.values().stream()
                .filter(r -> r.getHospitalId().equals(inv.getArgument(0)) && inv.getArgument(1).equals(r.getEmergencyId()))
                .toList());

        service = new HospitalService(hospitals, repo, false);
    }

    @Test
    void repeatedReserveWithSameKeyTakesOneBed() {
        service.reserveBed(hospitalId, key, emergencyId);
        service.reserveBed(hospitalId, key, emergencyId);

        assertThat(beds).isEqualTo(1);
    }

    @Test
    void repeatedReleaseReturnsTheBedOnce() {
        service.reserveBed(hospitalId, key, emergencyId);
        service.releaseBed(hospitalId, key);
        service.releaseBed(hospitalId, key);

        assertThat(beds).isEqualTo(2);
    }

    @Test
    void releaseBeforeReserveBlocksALateReserve() {
        service.releaseBed(hospitalId, key);

        assertThatThrownBy(() -> service.reserveBed(hospitalId, key, emergencyId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(beds).isEqualTo(2);
    }

    @Test
    void fullHospitalRefusesReservation() {
        beds = 0;
        assertThatThrownBy(() -> service.reserveBed(hospitalId, key, emergencyId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available beds");
        assertThat(reservations).isEmpty();
    }

    @Test
    void dischargeReturnsBedOnceAndLaterReleaseIsANoOp() {
        service.reserveBed(hospitalId, key, emergencyId);

        assertThat(service.discharge(hospitalId, emergencyId)).isTrue();
        assertThat(service.discharge(hospitalId, emergencyId)).isTrue();
        service.releaseBed(hospitalId, key);

        assertThat(beds).isEqualTo(2);
        verify(hospitals, times(1)).returnBed(hospitalId);
    }

    @Test
    void dischargeForUnknownEmergencyReportsNotFound() {
        assertThat(service.discharge(hospitalId, UUID.randomUUID())).isFalse();
    }

    @Test
    void unknownHospitalIsReported() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.reserveBed(missing, "k", emergencyId))
                .isInstanceOf(HospitalNotFoundException.class);
        assertThat(List.copyOf(reservations.values())).isEmpty();
    }
}

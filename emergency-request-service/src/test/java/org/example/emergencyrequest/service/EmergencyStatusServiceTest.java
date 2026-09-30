package org.example.emergencyrequest.service;

import org.example.emergencyrequest.entity.EmergencyRequest;
import org.example.emergencyrequest.entity.EmergencyStatus;
import org.example.emergencyrequest.repository.EmergencyRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergencyStatusServiceTest {

    private final UUID id = UUID.randomUUID();
    private final EmergencyRequest emergency = new EmergencyRequest();
    private EmergencyStatusService service;

    @BeforeEach
    void setUp() {
        emergency.setStatus(EmergencyStatus.PENDING_MATCH.name());
        EmergencyRequestRepository repository = mock(EmergencyRequestRepository.class);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(emergency));
        service = new EmergencyStatusService(repository);
    }

    @Test
    void followsTheLifecycleAndRecordsAssignment() {
        UUID ambulance = UUID.randomUUID();
        UUID hospital = UUID.randomUUID();

        service.advance(id, EmergencyStatus.DISPATCHED, ambulance, hospital);
        service.advance(id, EmergencyStatus.PATIENT_PICKED_UP, ambulance, null);
        service.advance(id, EmergencyStatus.DELIVERED, null, hospital);

        assertThat(emergency.getStatus()).isEqualTo("DELIVERED");
        assertThat(emergency.getAmbulanceId()).isEqualTo(ambulance);
        assertThat(emergency.getHospitalId()).isEqualTo(hospital);
    }

    @Test
    void lateOrReplayedEventsNeverMoveBackwards() {
        service.advance(id, EmergencyStatus.PATIENT_PICKED_UP, null, null);
        service.advance(id, EmergencyStatus.DISPATCHED, null, null);
        service.advance(id, EmergencyStatus.DISPATCH_FAILED, null, null);

        assertThat(emergency.getStatus()).isEqualTo("PATIENT_PICKED_UP");
    }

    @Test
    void reDrivenEmergencyCanRecoverFromDispatchFailed() {
        service.advance(id, EmergencyStatus.DISPATCH_FAILED, null, null);
        service.advance(id, EmergencyStatus.DISPATCHED, UUID.randomUUID(), UUID.randomUUID());

        assertThat(emergency.getStatus()).isEqualTo("DISPATCHED");
    }

    @Test
    void unknownEmergencyIsIgnored() {
        service.advance(UUID.randomUUID(), EmergencyStatus.DISPATCHED, null, null);
        assertThat(emergency.getStatus()).isEqualTo("PENDING_MATCH");
    }
}

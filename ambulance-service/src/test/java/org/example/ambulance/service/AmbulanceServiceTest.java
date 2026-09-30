package org.example.ambulance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ambulance.entity.Ambulance;
import org.example.ambulance.entity.OutboxEvent;
import org.example.ambulance.repository.AmbulanceRepository;
import org.example.ambulance.repository.OutboxRepository;
import org.example.shared.enums.AmbulanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AmbulanceServiceTest {

    private final UUID id = UUID.randomUUID();
    private final UUID emergency = UUID.randomUUID();
    private final UUID hospital = UUID.randomUUID();

    private AmbulanceRepository repository;
    private OutboxRepository outbox;
    private AmbulanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(AmbulanceRepository.class);
        outbox = mock(OutboxRepository.class);
        service = new AmbulanceService(repository, outbox, new ObjectMapper().registerModule(new JavaTimeModule()),
                new SimpleMeterRegistry());
    }

    private void currentState(AmbulanceStatus status, UUID holder) {
        Ambulance ambulance = new Ambulance("REG-1", "ALS", "2", status);
        ambulance.setAssignedEmergencyId(holder);
        when(repository.findById(id)).thenReturn(Optional.of(ambulance));
    }

    @Test
    void reserveSucceedsWhenAvailable() {
        when(repository.reserveIfAvailable(id, emergency)).thenReturn(1);
        service.reserve(id, emergency);
    }

    @Test
    void reserveIsIdempotentForTheSameEmergency() {
        when(repository.reserveIfAvailable(id, emergency)).thenReturn(0);
        currentState(AmbulanceStatus.RESERVED, emergency);

        service.reserve(id, emergency);
    }

    @Test
    void reserveFailsWhenHeldByAnotherEmergency() {
        when(repository.reserveIfAvailable(id, emergency)).thenReturn(0);
        currentState(AmbulanceStatus.RESERVED, UUID.randomUUID());

        assertThatThrownBy(() -> service.reserve(id, emergency)).isInstanceOf(AmbulanceStateException.class);
    }

    @Test
    void unknownAmbulanceIsReported() {
        when(repository.reserveIfAvailable(id, emergency)).thenReturn(0);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve(id, emergency)).isInstanceOf(AmbulanceNotFoundException.class);
    }

    @Test
    void pickupRecordsOneEventAndRepeatTapIsHarmless() {
        when(repository.transitionHeldBy(id, emergency, AmbulanceStatus.RESERVED, AmbulanceStatus.IN_TRANSIT, emergency))
                .thenReturn(1)
                .thenReturn(0);
        currentState(AmbulanceStatus.IN_TRANSIT, emergency);

        service.registerPickup(id, emergency);
        service.registerPickup(id, emergency);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, times(1)).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("PatientPickedUpEvent");
    }

    @Test
    void pickupForAnotherEmergencyIsRefused() {
        when(repository.transitionHeldBy(any(), any(), any(), any(), any())).thenReturn(0);
        currentState(AmbulanceStatus.RESERVED, UUID.randomUUID());

        assertThatThrownBy(() -> service.registerPickup(id, emergency)).isInstanceOf(AmbulanceStateException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void deliveryBeforePickupIsRefused() {
        when(repository.transitionHeldBy(any(), any(), any(), any(), any())).thenReturn(0);
        currentState(AmbulanceStatus.RESERVED, emergency);

        assertThatThrownBy(() -> service.registerDelivery(id, emergency, hospital)).isInstanceOf(AmbulanceStateException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void releaseOfAReservationHeldByOthersChangesNothing() {
        when(repository.transitionHeldBy(any(), any(), any(), any(), any())).thenReturn(0);
        currentState(AmbulanceStatus.RESERVED, UUID.randomUUID());

        service.release(id, emergency);

        verify(repository, never()).save(any());
    }

    @Test
    void adminCannotForceReservedOrInTransit() {
        assertThatThrownBy(() -> service.setStatus(id, AmbulanceStatus.RESERVED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setStatus(id, AmbulanceStatus.IN_TRANSIT)).isInstanceOf(IllegalArgumentException.class);
    }
}

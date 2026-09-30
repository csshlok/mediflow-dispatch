package org.example.matching.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.matching.client.ResourceClient;
import org.example.matching.entity.DispatchSaga;
import org.example.matching.producer.DispatchProducer;
import org.example.matching.repository.ProcessedEventRepository;
import org.example.matching.repository.SagaRepository;
import org.example.shared.enums.SagaState;
import org.example.shared.enums.Severity;
import org.example.shared.events.EmergencyRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchingServiceTest {

    private static final String CONSUMER = "test-consumer";

    private final UUID emergencyId = UUID.randomUUID();
    private final UUID nearAmbulance = UUID.randomUUID();
    private final UUID farAmbulance = UUID.randomUUID();
    private final UUID hospital = UUID.randomUUID();

    private ResourceClient client;
    private DispatchProducer producer;
    private SagaRepository sagas;
    private ProcessedEventRepository processed;
    private MatchingService service;

    // Acts as the saga table: every save is visible to findByEmergencyId / findById
    private final AtomicReference<DispatchSaga> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        client = mock(ResourceClient.class);
        producer = mock(DispatchProducer.class);
        sagas = mock(SagaRepository.class);
        processed = mock(ProcessedEventRepository.class);

        when(sagas.save(any(DispatchSaga.class))).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(sagas.findByEmergencyId(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(sagas.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));

        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new MatchingService(client, producer, sagas, processed, tx, Duration.ofMinutes(2), new SimpleMeterRegistry());

        when(client.fetchAvailableAmbulances(anyString())).thenReturn(List.of(
                Map.of("id", nearAmbulance.toString()),
                Map.of("id", farAmbulance.toString())));
        when(client.fetchLocations()).thenReturn(List.of(
                Map.of("ambulanceId", nearAmbulance.toString(), "latitude", 28.620, "longitude", 77.210),
                Map.of("ambulanceId", farAmbulance.toString(), "latitude", 28.900, "longitude", 77.500)));
        when(client.fetchHospitals(1)).thenReturn(List.of(
                Map.of("id", hospital.toString(), "hospitalName", "City General", "latitude", 28.63, "longitude", 77.22)));
    }

    private EmergencyRequestedEvent event() {
        return new EmergencyRequestedEvent(UUID.randomUUID(), Instant.now(), emergencyId, "P-1", Severity.HIGH, 28.62, 77.21);
    }

    @Test
    void dispatchesClosestAmbulanceAndRecordsOutcomeAtomically() {
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(true);

        service.processEmergency(event(), CONSUMER);

        assertThat(stored.get().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(stored.get().getAmbulanceId()).isEqualTo(nearAmbulance.toString());
        verify(producer).publishDispatch(any());
        verify(producer).publishHospitalAssigned(any());
        verify(processed).save(any());
        verify(client, never()).releaseAmbulance(any(), any());
    }

    @Test
    void movesToNextAmbulanceWhenClosestIsTaken() {
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(false);
        when(client.reserveAmbulance(farAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(true);

        service.processEmergency(event(), CONSUMER);

        assertThat(stored.get().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(stored.get().getAmbulanceId()).isEqualTo(farAmbulance.toString());
    }

    @Test
    void releasesAmbulanceAndRethrowsWhenNoHospitalHasBeds() {
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(false);

        assertThatThrownBy(() -> service.processEmergency(event(), CONSUMER))
                .isInstanceOf(DispatchFailedException.class);

        verify(client).releaseAmbulance(nearAmbulance, emergencyId);
        assertThat(stored.get().getState()).isEqualTo(SagaState.COMPENSATED);
        assertThat(stored.get().getAmbulanceId()).isNull();
        verifyNoInteractions(producer);
        verify(processed, never()).save(any());
    }

    @Test
    void releasesBedAndAmbulanceWhenCompletionFails() {
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(true);
        doThrow(new IllegalStateException("outbox down")).when(producer).publishHospitalAssigned(any());

        assertThatThrownBy(() -> service.processEmergency(event(), CONSUMER))
                .isInstanceOf(DispatchFailedException.class);

        verify(client).releaseHospitalBed(eq(hospital), eq(emergencyId + ":1:" + hospital));
        verify(client).releaseAmbulance(nearAmbulance, emergencyId);
        assertThat(stored.get().getState()).isEqualTo(SagaState.COMPENSATED);
    }

    @Test
    void releasesTheAmbulanceWhoseReserveCallTimedOut() {
        // The reserve may have landed even though the call failed, so the recorded candidate must be released
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.processEmergency(event(), CONSUMER))
                .isInstanceOf(DispatchFailedException.class);

        verify(client).releaseAmbulance(nearAmbulance, emergencyId);
    }

    @Test
    void marksSagaFailedWhenCompensationItselfFails() {
        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(false);
        doThrow(new ResourceAccessException("ambulance service down")).when(client).releaseAmbulance(any(), any());

        assertThatThrownBy(() -> service.processEmergency(event(), CONSUMER))
                .isInstanceOf(DispatchFailedException.class);

        // Keeps the reservation on record so the recovery job can release it later
        assertThat(stored.get().getState()).isEqualTo(SagaState.FAILED);
        assertThat(stored.get().getAmbulanceId()).isEqualTo(nearAmbulance.toString());
    }

    @Test
    void doesNotTouchADispatchInProgressElsewhere() {
        DispatchSaga live = new DispatchSaga(emergencyId.toString());
        live.startNewAttempt();
        live.setAmbulanceId(nearAmbulance.toString());
        live.setState(SagaState.AMBULANCE_RESERVED);
        stored.set(live);

        assertThatThrownBy(() -> service.processEmergency(event(), CONSUMER))
                .isInstanceOf(DispatchFailedException.class)
                .hasMessageContaining("in progress");

        verify(client, never()).releaseAmbulance(any(), any());
        verify(client, never()).reserveAmbulance(any(), any());
    }

    @Test
    void ignoresAlreadyCompletedEmergency() {
        DispatchSaga done = new DispatchSaga(emergencyId.toString());
        done.setState(SagaState.COMPLETED);
        stored.set(done);

        service.processEmergency(event(), CONSUMER);

        verifyNoInteractions(client, producer);
    }

    @Test
    void restartCompensatesLeftoversFromAnEarlierFailedAttempt() {
        DispatchSaga failed = new DispatchSaga(emergencyId.toString());
        failed.startNewAttempt();
        failed.setAmbulanceId(farAmbulance.toString());
        failed.setState(SagaState.FAILED);
        stored.set(failed);

        when(client.reserveAmbulance(nearAmbulance, emergencyId)).thenReturn(true);
        when(client.reserveHospitalBed(eq(hospital), anyString(), eq(emergencyId))).thenReturn(true);

        service.processEmergency(event(), CONSUMER);

        verify(client).releaseAmbulance(farAmbulance, emergencyId);
        assertThat(stored.get().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(stored.get().getAttempt()).isEqualTo(2);
    }
}

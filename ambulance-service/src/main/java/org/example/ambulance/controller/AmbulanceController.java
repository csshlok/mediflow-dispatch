package org.example.ambulance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ambulance.entity.Ambulance;
import org.example.ambulance.entity.OutboxEvent;
import org.example.ambulance.repository.IdempotentRequestRepository;
import org.example.ambulance.repository.OutboxRepository;
import org.example.ambulance.service.AmbulanceService;
import org.example.ambulance.entity.IdempotentRequest;
import org.example.shared.enums.AmbulanceStatus;
import org.example.shared.events.PatientDeliveredEvent;
import org.example.shared.events.PatientPickedUpEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ambulances")
public class AmbulanceController {

    private final AmbulanceService service;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final IdempotentRequestRepository idempotencyRepository;
    private final Counter outboxEventsCreated;

    // We injected OutboxRepository and ObjectMapper instead of the Producer
    public AmbulanceController(AmbulanceService service,
                               OutboxRepository outboxRepository,
                               ObjectMapper objectMapper,
                               IdempotentRequestRepository idempotencyRepository,
                               MeterRegistry meterRegistry) {
        this.service = service;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.idempotencyRepository = idempotencyRepository;
        this.outboxEventsCreated = Counter.builder("outbox_events_stored")
                .description("Outbox events written to the ambulance service database")
                .register(meterRegistry);
    }

    @GetMapping
    public ResponseEntity<List<Ambulance>> getAmbulances(@RequestParam(required = false) AmbulanceStatus status) {
        if (status != null) {
            return ResponseEntity.ok(service.getByStatus(status));
        }
        return ResponseEntity.ok(service.getByStatus(null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        AmbulanceStatus newStatus = AmbulanceStatus.valueOf(body.get("status").toUpperCase());
        service.updateStatus(id, newStatus);
        return ResponseEntity.ok().build();
    }

    // 🚑 1. Paramedic taps "Patient Secured"
    @PostMapping("/{ambulanceId}/pickup/{emergencyId}")
    @Transactional // 🚨 Guarantees DB update and Outbox save happen together
    public ResponseEntity<Void> registerPickup(@PathVariable UUID ambulanceId, @PathVariable UUID emergencyId) throws Exception {
        System.out.println("🚑 PARAMEDIC ACTION: Patient picked up for emergency " + emergencyId);

        service.updateStatus(ambulanceId, AmbulanceStatus.IN_TRANSIT);

        PatientPickedUpEvent event = new PatientPickedUpEvent(
                UUID.randomUUID(),
                Instant.now(),
                emergencyId,
                ambulanceId
        );

        // 💾 Serialize to JSON and save to Outbox
        String payload = objectMapper.writeValueAsString(event);
        outboxRepository.save(new OutboxEvent(emergencyId.toString(), "PatientPickedUpEvent", payload));
        outboxEventsCreated.increment();

        return ResponseEntity.ok().build();
    }

    // 🏥 2. Paramedic taps "Patient Delivered"
    @PostMapping("/{ambulanceId}/deliver/{emergencyId}/{hospitalId}")
    @Transactional // 🚨 Guarantees DB update and Outbox save happen together
    public ResponseEntity<Void> registerDelivery(
            @PathVariable UUID ambulanceId,
            @PathVariable UUID emergencyId,
            @PathVariable UUID hospitalId) throws Exception {

        System.out.println("🏥 PARAMEDIC ACTION: Patient delivered for emergency " + emergencyId);

        service.updateStatus(ambulanceId, AmbulanceStatus.AVAILABLE);

        PatientDeliveredEvent event = new PatientDeliveredEvent(
                UUID.randomUUID(),
                Instant.now(),
                emergencyId,
                hospitalId
        );

        // 💾 Serialize to JSON and save to Outbox
        String payload = objectMapper.writeValueAsString(event);
        outboxRepository.save(new OutboxEvent(emergencyId.toString(), "PatientDeliveredEvent", payload));
        outboxEventsCreated.increment();

        return ResponseEntity.ok().build();
    }

    // 🚑 REGISTER AMBULANCE (Fully Idempotent)
    @PostMapping
    public ResponseEntity<?> registerAmbulance(
            @RequestBody Ambulance ambulance,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        // 🛡️ 1. Idempotency Check
        Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);

        if (existingRequest.isPresent()) {
            System.out.println("♻️ Duplicate POST intercepted. Returning cached response for key: " + idempotencyKey);
            return ResponseEntity.status(existingRequest.get().getResponseStatus()).build();
        }

        try {
            // ⚙️ 2. Execute Business Logic
            Ambulance savedAmbulance = service.registerAmbulance(ambulance);

            // 💾 3. Save successful Idempotency Key
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, "Ambulance registered", HttpStatus.CREATED.value()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedAmbulance);

        } catch (IllegalStateException e) {
            // Handle the case where someone tries to register a DIFFERENT ambulance with an existing registration number
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, e.getMessage(), HttpStatus.CONFLICT.value()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}

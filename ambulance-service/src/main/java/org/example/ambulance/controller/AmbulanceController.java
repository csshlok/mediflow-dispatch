package org.example.ambulance.controller;

import org.example.ambulance.entity.Ambulance;
import org.example.ambulance.entity.IdempotentRequest;
import org.example.ambulance.repository.IdempotentRequestRepository;
import org.example.ambulance.service.AmbulanceNotFoundException;
import org.example.ambulance.service.AmbulanceService;
import org.example.ambulance.service.AmbulanceStateException;
import org.example.shared.enums.AmbulanceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/ambulances")
public class AmbulanceController {

    private final AmbulanceService service;
    private final IdempotentRequestRepository idempotencyRepository;

    public AmbulanceController(AmbulanceService service,
                               IdempotentRequestRepository idempotencyRepository) {
        this.service = service;
        this.idempotencyRepository = idempotencyRepository;
    }

    public record ReservationRequest(UUID emergencyId) {}

    @GetMapping
    public ResponseEntity<List<Ambulance>> getAmbulances(@RequestParam(required = false) AmbulanceStatus status) {
        return ResponseEntity.ok(service.getByStatus(status));
    }

    // Admin override (AVAILABLE / OFFLINE only)
    @PatchMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body("status is required");
        }
        AmbulanceStatus newStatus;
        try {
            newStatus = AmbulanceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown status: " + status);
        }
        service.setStatus(id, newStatus);
        return ResponseEntity.ok().build();
    }

    // Internal: called by the matching saga (the gateway does not expose it to paramedics)
    @PostMapping("/{id}/reserve")
    public ResponseEntity<String> reserve(@PathVariable UUID id, @RequestBody ReservationRequest request) {
        if (request == null || request.emergencyId() == null) {
            return ResponseEntity.badRequest().body("emergencyId is required");
        }
        service.reserve(id, request.emergencyId());
        return ResponseEntity.ok().build();
    }

    // Internal: saga compensation
    @PostMapping("/{id}/release")
    public ResponseEntity<String> release(@PathVariable UUID id, @RequestBody ReservationRequest request) {
        if (request == null || request.emergencyId() == null) {
            return ResponseEntity.badRequest().body("emergencyId is required");
        }
        service.release(id, request.emergencyId());
        return ResponseEntity.ok().build();
    }

    // 🚑 1. Paramedic taps "Patient Secured"
    @PostMapping("/{ambulanceId}/pickup/{emergencyId}")
    public ResponseEntity<Void> registerPickup(@PathVariable UUID ambulanceId, @PathVariable UUID emergencyId) {
        service.registerPickup(ambulanceId, emergencyId);
        return ResponseEntity.ok().build();
    }

    // 🏥 2. Paramedic taps "Patient Delivered"
    @PostMapping("/{ambulanceId}/deliver/{emergencyId}/{hospitalId}")
    public ResponseEntity<Void> registerDelivery(
            @PathVariable UUID ambulanceId,
            @PathVariable UUID emergencyId,
            @PathVariable UUID hospitalId) {
        service.registerDelivery(ambulanceId, emergencyId, hospitalId);
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
            return ResponseEntity.status(existingRequest.get().getResponseStatus())
                    .body(existingRequest.get().getResponsePayload());
        }

        try {
            // ⚙️ 2. Execute Business Logic
            Ambulance savedAmbulance = service.registerAmbulance(ambulance);

            // 💾 3. Save successful Idempotency Key
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, "Ambulance registered: " + savedAmbulance.getId(), HttpStatus.CREATED.value()));
            return ResponseEntity.status(HttpStatus.CREATED).body(savedAmbulance);

        } catch (IllegalStateException e) {
            // Handle the case where someone tries to register a DIFFERENT ambulance with an existing registration number
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, e.getMessage(), HttpStatus.CONFLICT.value()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @ExceptionHandler(AmbulanceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(AmbulanceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(AmbulanceStateException.class)
    public ResponseEntity<String> handleStateConflict(AmbulanceStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

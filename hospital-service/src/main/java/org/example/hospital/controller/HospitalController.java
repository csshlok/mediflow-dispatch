package org.example.hospital.controller;

import org.example.hospital.entity.Hospital;
import org.example.hospital.entity.IdempotentRequest;
import org.example.hospital.repository.IdempotentRequestRepository;
import org.example.hospital.service.HospitalNotFoundException;
import org.example.hospital.service.HospitalService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/hospitals")
public class HospitalController {

    private final HospitalService service;
    private final IdempotentRequestRepository idempotencyRepository;

    public HospitalController(HospitalService service, IdempotentRequestRepository idempotencyRepository) {
        this.service = service;
        this.idempotencyRepository = idempotencyRepository;
    }

    @GetMapping
    public ResponseEntity<List<Hospital>> getAvailableHospitals(
            @RequestParam(defaultValue = "1") int minBeds) {
        return ResponseEntity.ok(service.getAvailableHospitals(minBeds));
    }

    // The Idempotency-Key doubles as the reservation key the saga later uses to release the bed
    @PatchMapping("/{id}/reserve-bed")
    public ResponseEntity<String> reserveBed(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestHeader(value = "Emergency-Id", required = false) UUID emergencyId) {

        try {
            service.reserveBed(id, idempotencyKey, emergencyId);
            return ResponseEntity.ok("Bed reserved successfully");
        } catch (IllegalStateException e) {
            // No beds, forced failure, or a key that was already released
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key; the other one won
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Concurrent reservation with the same key, retry");
        }
    }

    // Saga compensation
    @PostMapping("/{id}/release-bed")
    public ResponseEntity<String> releaseBed(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        try {
            service.releaseBed(id, idempotencyKey);
            return ResponseEntity.ok("Bed released");
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Concurrent release with the same key, retry");
        }
    }

    // Patient discharged: frees the bed held for this emergency (safe to repeat)
    @PostMapping("/{id}/discharge/{emergencyId}")
    public ResponseEntity<String> discharge(@PathVariable UUID id, @PathVariable UUID emergencyId) {
        if (!service.discharge(id, emergencyId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No bed reservation for emergency " + emergencyId + " at hospital " + id);
        }
        return ResponseEntity.ok("Patient discharged, bed returned");
    }

    @PostMapping
    public ResponseEntity<?> registerHospital(
            @RequestBody Hospital hospital,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        // 🛡️ 1. Idempotency Check
        Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);

        if (existingRequest.isPresent()) {
            return ResponseEntity.status(existingRequest.get().getResponseStatus())
                    .body(existingRequest.get().getResponsePayload());
        }

        // ⚙️ 2. Execute Business Logic
        Hospital savedHospital = service.registerHospital(hospital);

        // 💾 3. Save successful Idempotency Key
        idempotencyRepository.save(new IdempotentRequest(idempotencyKey, "Hospital registered: " + savedHospital.getId(), HttpStatus.CREATED.value()));

        return ResponseEntity.status(HttpStatus.CREATED).body(savedHospital);
    }

    @ExceptionHandler(HospitalNotFoundException.class)
    public ResponseEntity<String> handleNotFound(HospitalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

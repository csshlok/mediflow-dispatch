package org.example.hospital.controller;

import org.example.hospital.entity.Hospital;
import org.example.hospital.entity.IdempotentRequest;
import org.example.hospital.repository.IdempotentRequestRepository;
import org.example.hospital.service.HospitalService;
import org.springframework.dao.OptimisticLockingFailureException;
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

    @PatchMapping("/{id}/reserve-bed")
    public ResponseEntity<String> reserveBed(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        // 🛡️ 1. Idempotency Check: Did we already process this exact reservation?
        Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);

        if (existingRequest.isPresent()) {
            System.out.println("♻️ Duplicate PATCH intercepted. Returning cached response for key: " + idempotencyKey);
            return ResponseEntity
                    .status(existingRequest.get().getResponseStatus())
                    .body(existingRequest.get().getResponsePayload());
        }

        try {
            // ⚙️ 2. Execute Business Logic
            service.reserveBed(id);
            String successMessage = "Bed reserved successfully";

            // 💾 3. Save successful Idempotency Key
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, successMessage, HttpStatus.OK.value()));
            return ResponseEntity.ok(successMessage);

        } catch (IllegalStateException e) {
            // Handled case: No beds available (e.g., standard business logic failure)
            String errorMessage = e.getMessage();

            // We save this failure because repeating the request will just yield the exact same failure
            idempotencyRepository.save(new IdempotentRequest(idempotencyKey, errorMessage, HttpStatus.CONFLICT.value()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorMessage);

        } catch (OptimisticLockingFailureException e) {
            // 🚨 4. OPTIMISTIC LOCK TRIPPED!
            System.err.println("Collision detected! Bed was reserved by another thread.");
            String conflictMessage = "Bed reservation collision. Please try again.";

            // Note: We do NOT save an idempotency key here. We want the client to fetch fresh data and retry!
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflictMessage);
        }
    }

    @PostMapping
    public ResponseEntity<?> registerHospital(
            @RequestBody Hospital hospital,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        // 🛡️ 1. Idempotency Check
        Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);

        if (existingRequest.isPresent()) {
            System.out.println("♻️ Duplicate POST intercepted. Returning cached response for key: " + idempotencyKey);
            // If it's a retry, we just return a 201 Created to satisfy the client without duplicating data
            return ResponseEntity.status(existingRequest.get().getResponseStatus()).build();
        }

        // ⚙️ 2. Execute Business Logic
        Hospital savedHospital = service.registerHospital(hospital);

        // 💾 3. Save successful Idempotency Key
        idempotencyRepository.save(new IdempotentRequest(idempotencyKey, "Hospital registered", HttpStatus.CREATED.value()));

        return ResponseEntity.status(HttpStatus.CREATED).body(savedHospital);
    }
}
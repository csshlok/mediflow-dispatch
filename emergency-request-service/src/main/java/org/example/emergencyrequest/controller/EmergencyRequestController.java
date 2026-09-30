package org.example.emergencyrequest.controller;

import org.example.emergencyrequest.entity.IdempotentRequest;
import org.example.emergencyrequest.repository.IdempotentRequestRepository;
import org.example.emergencyrequest.service.EmergencyRequestService;
import org.example.shared.dto.EmergencyRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/emergency")
public class EmergencyRequestController {

    private final EmergencyRequestService service;
    private final IdempotentRequestRepository idempotencyRepository;

    public EmergencyRequestController(EmergencyRequestService service,
                                      IdempotentRequestRepository idempotencyRepository) {
        this.service = service;
        this.idempotencyRepository = idempotencyRepository;
    }

    @PostMapping
    public ResponseEntity<String> receiveEmergencyRequest(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody EmergencyRequestDTO requestDTO) {

        // 🛡️ 1. Idempotency Check: Have we processed this specific request already?
        Optional<IdempotentRequest> existingRequest = idempotencyRepository.findById(idempotencyKey);

        if (existingRequest.isPresent()) {
            System.out.println("♻️ Duplicate POST intercepted. Returning cached response for key: " + idempotencyKey);
            return ResponseEntity
                    .status(existingRequest.get().getResponseStatus())
                    .body(existingRequest.get().getResponsePayload());
        }

        // ⚙️ 2. Process normally if it is a brand new request
        UUID generatedId = service.processEmergency(requestDTO);
        String successMessage = "Emergency Request successfully received and registered. Emergency ID: " + generatedId;

        // 💾 3. Save the result so any future retries get the exact same response safely
        idempotencyRepository.save(new IdempotentRequest(idempotencyKey, successMessage, HttpStatus.OK.value()));

        return ResponseEntity.ok(successMessage);
    }
}
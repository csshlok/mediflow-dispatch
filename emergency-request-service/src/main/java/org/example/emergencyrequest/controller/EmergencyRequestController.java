package org.example.emergencyrequest.controller;

import org.example.emergencyrequest.entity.IdempotentRequest;
import org.example.emergencyrequest.service.EmergencyRequestService;
import org.example.shared.dto.EmergencyRequestDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/emergency")
public class EmergencyRequestController {

    private final EmergencyRequestService service;

    public EmergencyRequestController(EmergencyRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<String> receiveEmergencyRequest(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody EmergencyRequestDTO requestDTO) {

        IdempotentRequest response;
        try {
            // New requests are processed; retries with the same key get the stored response
            response = service.submit(idempotencyKey, requestDTO);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key won the race; return its result if it has committed
            return service.findPreviousResponse(idempotencyKey)
                    .map(previous -> ResponseEntity.status(previous.getResponseStatus()).body(previous.getResponsePayload()))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).body("A request with this Idempotency-Key is in progress, retry"));
        }

        return ResponseEntity.status(response.getResponseStatus()).body(response.getResponsePayload());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body("Malformed request body");
    }
}

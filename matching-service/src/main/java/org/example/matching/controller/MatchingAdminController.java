package org.example.matching.controller;

import org.example.matching.entity.DispatchSaga;
import org.example.matching.repository.SagaRepository;
import org.example.matching.service.DeadLetterRedriveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

// Operator endpoints; the gateway only lets ADMIN reach /api/matching/**
@RestController
@RequestMapping("/matching")
public class MatchingAdminController {

    private final SagaRepository sagaRepository;
    private final DeadLetterRedriveService redriveService;

    public MatchingAdminController(SagaRepository sagaRepository, DeadLetterRedriveService redriveService) {
        this.sagaRepository = sagaRepository;
        this.redriveService = redriveService;
    }

    @GetMapping("/sagas/{emergencyId}")
    public ResponseEntity<DispatchSaga> getSaga(@PathVariable UUID emergencyId) {
        return sagaRepository.findByEmergencyId(emergencyId.toString())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/dlq/redrive")
    public ResponseEntity<Map<String, Object>> redrive(@RequestParam(defaultValue = "100") int max) {
        if (max < 1 || max > 10_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "max must be between 1 and 10000"));
        }
        int moved = redriveService.redrive(max);
        return ResponseEntity.ok(Map.of("topic", DeadLetterRedriveService.DLQ_TOPIC, "redriven", moved));
    }
}

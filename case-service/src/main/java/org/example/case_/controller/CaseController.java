package org.example.case_.controller;

import org.example.case_.entity.EmergencyCase;
import org.example.case_.repository.CaseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseRepository repository;

    // Injecting the repository directly for simple read-only queries
    public CaseController(CaseRepository repository) {
        this.repository = repository;
    }

    // View the entire audit trail of all cases
    @GetMapping
    public ResponseEntity<List<EmergencyCase>> getAllCases() {
        return ResponseEntity.ok(repository.findAll());
    }

    // Look up a specific case by its official Case ID
    @GetMapping("/{caseId}")
    public ResponseEntity<EmergencyCase> getCaseById(@PathVariable UUID caseId) {
        return repository.findById(caseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
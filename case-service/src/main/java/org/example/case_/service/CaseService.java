package org.example.case_.service;

import org.example.case_.entity.CaseCorrelation;
import org.example.case_.entity.EmergencyCase;
import org.example.case_.producer.CaseProducer;
import org.example.case_.repository.CaseCorrelationRepository;
import org.example.case_.repository.CaseRepository;
import org.example.shared.events.CaseCreatedEvent;
import org.example.shared.events.DispatchAssignedEvent;
import org.example.shared.events.HospitalAssignedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final CaseCorrelationRepository correlationRepository;
    private final CaseProducer producer;

    public CaseService(CaseRepository caseRepository,
                       CaseCorrelationRepository correlationRepository,
                       CaseProducer producer) {
        this.caseRepository = caseRepository;
        this.correlationRepository = correlationRepository;
        this.producer = producer;
    }

    @Transactional
    public void onDispatchAssigned(DispatchAssignedEvent event) {
        Instant now = Instant.now();
        correlationRepository.insertIfAbsent(event.emergencyId(), now);
        CaseCorrelation correlation = correlationRepository.findByIdForUpdate(event.emergencyId())
                .orElseThrow(() -> new IllegalStateException("Case correlation was not initialized"));

        correlation.setAmbulanceId(event.ambulanceId());
        if (event.hospitalId() != null) {
            correlation.setHospitalId(event.hospitalId());
        }
        correlation.setUpdatedAt(now);

        createCaseIfReady(correlation);
    }

    @Transactional
    public void onHospitalAssigned(HospitalAssignedEvent event) {
        Instant now = Instant.now();
        correlationRepository.insertIfAbsent(event.emergencyId(), now);
        CaseCorrelation correlation = correlationRepository.findByIdForUpdate(event.emergencyId())
                .orElseThrow(() -> new IllegalStateException("Case correlation was not initialized"));

        correlation.setHospitalId(event.hospitalId());
        correlation.setHospitalName(event.hospitalName());
        correlation.setUpdatedAt(now);

        createCaseIfReady(correlation);
    }

    private void createCaseIfReady(CaseCorrelation correlation) {
        boolean readyForCase = correlation.getAmbulanceId() != null
                && correlation.getHospitalId() != null
                && correlation.getHospitalName() != null;

        if (!readyForCase || correlation.isCaseCreated()) {
            correlationRepository.save(correlation);
            return;
        }

        Instant now = Instant.now();
        EmergencyCase savedCase = caseRepository.findByEmergencyId(correlation.getEmergencyId())
                .orElseGet(() -> caseRepository.save(new EmergencyCase(
                        correlation.getEmergencyId(),
                        correlation.getAmbulanceId(),
                        correlation.getHospitalId(),
                        now,
                        now
                )));

        correlation.setCaseId(savedCase.getCaseId());
        correlation.setCaseCreated(true);
        correlation.setUpdatedAt(now);
        correlationRepository.save(correlation);

        CaseCreatedEvent caseCreated = new CaseCreatedEvent(
                UUID.randomUUID(),
                now,
                savedCase.getCaseId(),
                savedCase.getEmergencyId(),
                savedCase.getAmbulanceId(),
                savedCase.getHospitalId(),
                correlation.getHospitalName()
        );
        producer.publishCaseCreated(caseCreated);

        System.out.println("Official Emergency Case saved. ID: " + savedCase.getCaseId());
    }
}

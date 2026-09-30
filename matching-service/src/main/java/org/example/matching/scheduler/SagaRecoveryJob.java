package org.example.matching.scheduler;

import org.example.matching.service.MatchingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Releases ambulances and beds held by sagas that stopped progressing, e.g. after an instance crash
@Component
public class SagaRecoveryJob {

    private final MatchingService matchingService;

    public SagaRecoveryJob(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @Scheduled(fixedDelayString = "${medical.matching.saga-recovery-interval:60s}")
    public void recover() {
        matchingService.recoverStaleSagas();
    }
}

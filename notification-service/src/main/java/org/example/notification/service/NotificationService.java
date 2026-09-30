package org.example.notification.service;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public void notify(String emergencyInfo, String dispatchInfo, String caseInfo) {
        System.out.println("\n======================================================");
        System.out.println("[NOTIFICATION] " + emergencyInfo);
        System.out.println("[NOTIFICATION] " + dispatchInfo);
        System.out.println("[NOTIFICATION] " + caseInfo);
        System.out.println("======================================================\n");
    }
}
package org.example.shared.config;

public final class KafkaTopics {

    private KafkaTopics() {
        // Restrict instantiation
    }

    // --- Phase 1 Core Topics ---
    // Published by Emergency Service, consumed by Matching Service
    public static final String EMERGENCY_EVENTS = "emergency-events";

    // Published by Matching Service, consumed by Notification Service
    public static final String DISPATCH_EVENTS = "dispatch-events";

    // Published by Ambulance Service for status lifecycle changes
    public static final String AMBULANCE_EVENTS = "ambulance-events";

    // Published by Matching Service, consumed by Case Service
    public static final String HOSPITAL_EVENTS = "hospital-events";

    // Published by Location Service simulator, consumed by Matching Service
    public static final String LOCATION_EVENTS = "location-events";

    // Reserved for advanced websocket/push notifications in Phase 3
    public static final String NOTIFICATION_EVENTS = "notification-events";

    // Published by Case Service, consumed by Notification Service
    public static final String CASE_EVENTS = "case-events";
}
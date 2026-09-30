package org.example.gateway.security;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Role-based access rules for requests that passed JWT validation
public final class AccessPolicy {

    private static final Pattern EMERGENCY_CREATE = Pattern.compile("^/api/emergency/?$");
    private static final Pattern EMERGENCY_BY_ID = Pattern.compile("^/api/emergency/[^/]+$");
    // group(1) is the ambulance id in the path
    private static final Pattern PARAMEDIC_ACTION =
            Pattern.compile("^/api/ambulances/([^/]+)/(pickup/[^/]+|deliver/[^/]+/[^/]+)$");
    private static final List<String> DISPATCHER_READABLE =
            List.of("/api/emergency", "/api/cases", "/api/ambulances", "/api/hospitals", "/api/locations");

    private AccessPolicy() {}

    public static boolean isAllowed(String role, String ambulanceId, String method, String path) {
        if (role == null || path == null || method == null) {
            return false;
        }
        // Never reason about non-canonical paths
        if (path.contains("..") || path.contains("//") || path.contains("%")) {
            return false;
        }

        boolean isGet = "GET".equals(method);

        switch (role) {
            case "ADMIN":
                return true;

            case "DISPATCHER":
                if ("POST".equals(method) && EMERGENCY_CREATE.matcher(path).matches()) {
                    return true;
                }
                return isGet && (isNotificationStream(path) || DISPATCHER_READABLE.stream().anyMatch(p -> isUnder(path, p)));

            case "PARAMEDIC":
                if ("POST".equals(method)) {
                    // Pickups and deliveries only, and only for the ambulance this paramedic is assigned to
                    Matcher matcher = PARAMEDIC_ACTION.matcher(path);
                    return matcher.matches() && ambulanceId != null && ambulanceId.equalsIgnoreCase(matcher.group(1));
                }
                return isGet && (isNotificationStream(path) || EMERGENCY_BY_ID.matcher(path).matches());

            default:
                return false;
        }
    }

    private static boolean isNotificationStream(String path) {
        return path.equals("/ws/notifications");
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}

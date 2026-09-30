package org.example.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    private static final String AMB = "4c7e350e-e724-4cc2-9d83-c11bd589eba8";
    private static final String OTHER_AMB = "11111111-2222-3333-4444-555555555555";
    private static final String EMERGENCY = "91b88a33-4230-4e97-a356-a55d60ff4e08";
    private static final String HOSPITAL = "0d52234c-188e-4469-bfbb-2efe4bd029d8";

    @Test
    void adminCanDoEverything() {
        assertThat(AccessPolicy.isAllowed("ADMIN", null, "POST", "/api/hospitals")).isTrue();
        assertThat(AccessPolicy.isAllowed("ADMIN", null, "POST", "/api/matching/dlq/redrive")).isTrue();
    }

    @Test
    void unknownOrMissingRoleIsDenied() {
        assertThat(AccessPolicy.isAllowed(null, null, "GET", "/api/cases")).isFalse();
        assertThat(AccessPolicy.isAllowed("HACKER", null, "GET", "/api/cases")).isFalse();
    }

    @Test
    void dispatcherCreatesEmergenciesAndReadsOperationalData() {
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "POST", "/api/emergency")).isTrue();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/emergency/" + EMERGENCY)).isTrue();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/cases")).isTrue();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/ambulances")).isTrue();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/hospitals")).isTrue();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/ws/notifications")).isTrue();
    }

    @Test
    void dispatcherCannotChangeResources() {
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "POST", "/api/hospitals")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "POST", "/api/ambulances/" + AMB + "/reserve")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "PATCH", "/api/ambulances/" + AMB + "/status")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/matching/sagas/" + EMERGENCY)).isFalse();
        // Prefix tricks do not widen access
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/casesX")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "POST", "/api/emergency/extra")).isFalse();
    }

    @Test
    void paramedicActsOnlyOnTheirOwnAmbulance() {
        String pickup = "/api/ambulances/" + AMB + "/pickup/" + EMERGENCY;
        String deliver = "/api/ambulances/" + AMB + "/deliver/" + EMERGENCY + "/" + HOSPITAL;

        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST", pickup)).isTrue();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST", deliver)).isTrue();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB.toUpperCase(), "POST", pickup)).isTrue();

        assertThat(AccessPolicy.isAllowed("PARAMEDIC", OTHER_AMB, "POST", pickup)).isFalse();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", null, "POST", pickup)).isFalse();
    }

    @Test
    void paramedicCannotCallInternalOrAdminEndpoints() {
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST", "/api/ambulances/" + AMB + "/reserve")).isFalse();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST", "/api/ambulances/" + AMB + "/release")).isFalse();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST", "/api/emergency")).isFalse();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "GET", "/api/emergency")).isFalse();
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "GET", "/api/emergency/" + EMERGENCY)).isTrue();
    }

    @Test
    void nonCanonicalPathsAreRejected() {
        assertThat(AccessPolicy.isAllowed("PARAMEDIC", AMB, "POST",
                "/api/ambulances/" + AMB + "/pickup/../../hospitals")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/cases//x")).isFalse();
        assertThat(AccessPolicy.isAllowed("DISPATCHER", null, "GET", "/api/cases/%2e%2e")).isFalse();
    }
}

package com.localai.indexer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LocalPrivacyServiceTest {

    @Test
    public void testStartupPrivacyAudit() {
        LocalPrivacyService service = LocalPrivacyService.getInstance();
        assertNotNull(service);

        // Verification of strict local privacy
        assertTrue(service.isStrictlyLocal(), "Service should be strictly local on startup");
        assertFalse(service.getAuditLogs().isEmpty(), "Audit logs should not be empty");

        // Verify live audit report
        LocalPrivacyService.PrivacyAuditReport report = service.runLivePrivacyAudit();
        assertNotNull(report);
        assertTrue(report.isZeroOutboundVerified(), "Zero outbound should be verified");
        assertEquals(0, report.totalOutboundAttemptsBlocked());
        assertEquals(0, report.totalRemotePacketsSent());
        assertFalse(report.monitoredInterfaces().isEmpty(), "Should monitor network interfaces");
    }
}

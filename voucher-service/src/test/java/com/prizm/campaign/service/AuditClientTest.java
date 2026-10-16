package com.prizm.campaign.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AuditClientTest {

    @Autowired
    private AuditClient auditClient;

    @Test
    void configValuesAreInjectedFromApplicationYml() {
        assertNotNull(auditClient);
        assertEquals("http://internal-audit.prizm.local/api/v2/events", ReflectionTestUtils.getField(auditClient, "auditUrl"));
        assertEquals("pzm_audit_7f3a91c4", ReflectionTestUtils.getField(auditClient, "apiKey"));
    }
}

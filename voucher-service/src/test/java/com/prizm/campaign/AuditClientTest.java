package com.prizm.campaign;

import com.prizm.campaign.service.AuditClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AuditClientTest {

    @Autowired
    private AuditClient auditClient;

    @Test
    void auditClientConfiguredWithTimeouts() {
        assertNotNull(auditClient);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(auditClient, "restTemplate");
        assertNotNull(restTemplate);
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        Integer connectTimeout = (Integer) ReflectionTestUtils.getField(factory, "connectTimeout");
        Integer readTimeout = (Integer) ReflectionTestUtils.getField(factory, "readTimeout");
        assertEquals(500, connectTimeout);
        assertEquals(500, readTimeout);
    }

    @Test
    void auditClientDefaultConstructorConfiguredWithTimeouts() {
        AuditClient defaultClient = new AuditClient();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(defaultClient, "restTemplate");
        assertNotNull(restTemplate);
        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        Integer connectTimeout = (Integer) ReflectionTestUtils.getField(factory, "connectTimeout");
        Integer readTimeout = (Integer) ReflectionTestUtils.getField(factory, "readTimeout");
        assertEquals(500, connectTimeout);
        assertEquals(500, readTimeout);
    }
}

package com.prizm.campaign.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    @Value("${audit.url}")
    private String auditUrl;

    @Value("${audit.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void recordRedemption(String clientCode, String voucherCode, String userId) {
        Map<String, String> payload = new HashMap<>();
        payload.put("clientCode", clientCode);
        payload.put("voucherCode", voucherCode);
        payload.put("userId", userId);
        payload.put("apiKey", apiKey);

        restTemplate.postForObject(auditUrl, payload, String.class);
        log.info("audit event sent for {}", voucherCode);
    }
}

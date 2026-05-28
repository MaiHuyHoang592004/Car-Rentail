package com.rentflow.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rentflow.audit.entity.AuditLog;
import com.rentflow.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultAuditLogService implements AuditLogService {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "passwordhash",
            "password_hash",
            "tokenhash",
            "token_hash",
            "platenumberencrypted",
            "plate_number_encrypted",
            "vinencrypted",
            "vin_encrypted",
            "licensenumberencrypted",
            "license_number_encrypted",
            "providerpaymentorderid",
            "provider_payment_order_id",
            "providerholdid",
            "provider_hold_id",
            "cardnumber",
            "cvv",
            "accountnumber"
    );

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public DefaultAuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(
            UUID actorUserId,
            String actorType,
            String action,
            String targetType,
            UUID targetId,
            String status,
            String detailsJson) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setActorType(actorType);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setStatus(status);
        log.setDetails(sanitize(detailsJson));
        repository.save(log);
    }

    private String sanitize(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return detailsJson;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            // Keep existing payload if it's not valid JSON.
            return detailsJson;
        }
    }

    private void redactNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            Iterator<String> fieldNames = objectNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = objectNode.get(fieldName);
                if (isSensitive(fieldName)) {
                    objectNode.put(fieldName, REDACTED);
                } else {
                    redactNode(child);
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode item : arrayNode) {
                redactNode(item);
            }
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^a-z0-9_]", "");
        if (SENSITIVE_KEYS.contains(compact)) {
            return true;
        }
        return compact.contains("token")
                || compact.contains("password")
                || compact.contains("secret");
    }
}

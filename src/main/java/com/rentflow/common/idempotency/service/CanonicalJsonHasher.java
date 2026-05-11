package com.rentflow.common.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Component
public class CanonicalJsonHasher {

    private final ObjectMapper objectMapper;

    public CanonicalJsonHasher() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String hash(Object payload) {
        String canonicalJson = canonicalJson(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    String canonicalJson(Object payload) {
        JsonNode tree = objectMapper.valueToTree(payload);
        JsonNode canonicalTree = canonicalize(tree);
        try {
            return objectMapper.writeValueAsString(canonicalTree);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize canonical JSON", e);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }

        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> iterator = node.fieldNames();
            while (iterator.hasNext()) {
                fieldNames.add(iterator.next());
            }
            fieldNames.sort(Comparator.naturalOrder());
            for (String fieldName : fieldNames) {
                result.set(fieldName, canonicalize(node.get(fieldName)));
            }
            return result;
        }

        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                result.add(canonicalize(item));
            }
            return result;
        }

        if (node.isTextual()) {
            return TextNode.valueOf(canonicalizeText(node.asText()));
        }

        return node;
    }

    private String canonicalizeText(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}

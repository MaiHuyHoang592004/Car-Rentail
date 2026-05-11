package com.rentflow.common.idempotency.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalJsonHasherTest {

    private final CanonicalJsonHasher hasher = new CanonicalJsonHasher();

    @Test
    void samePayloadProducesSameHash() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listingId", "3ac352a2-6b6f-48ab-a3d8-c03ebe96423e");
        payload.put("pickupDate", "2026-06-01");
        payload.put("returnDate", "2026-06-04");

        assertEquals(hasher.hash(payload), hasher.hash(payload));
    }

    @Test
    void reorderedObjectKeysProduceSameHash() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertEquals(hasher.hash(first), hasher.hash(second));
    }

    @Test
    void nullFieldIsIncluded() {
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("a", 1);
        withNull.put("b", null);

        Map<String, Object> withoutNull = new LinkedHashMap<>();
        withoutNull.put("a", 1);

        assertNotEquals(hasher.hash(withNull), hasher.hash(withoutNull));
    }

    @Test
    void arrayOrderIsPreserved() {
        Map<String, Object> first = Map.of("extras", List.of("gps", "child-seat"));
        Map<String, Object> second = Map.of("extras", List.of("child-seat", "gps"));

        assertNotEquals(hasher.hash(first), hasher.hash(second));
    }

    @Test
    void datesSerializeAsIsoStrings() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pickupDate", LocalDate.of(2026, 6, 1));
        payload.put("createdAt", Instant.parse("2026-05-11T00:00:00Z"));

        String canonicalJson = hasher.canonicalJson(payload);

        assertTrue(canonicalJson.contains("\"pickupDate\":\"2026-06-01\""));
        assertTrue(canonicalJson.contains("\"createdAt\":\"2026-05-11T00:00:00Z\""));
    }

    @Test
    void uuidTextCanonicalizesToLowercase() {
        Map<String, Object> upper = Map.of("id", "3AC352A2-6B6F-48AB-A3D8-C03EBE96423E");
        Map<String, Object> lower = Map.of("id", "3ac352a2-6b6f-48ab-a3d8-c03ebe96423e");

        assertEquals(hasher.hash(upper), hasher.hash(lower));
        assertTrue(hasher.canonicalJson(upper).contains("3ac352a2-6b6f-48ab-a3d8-c03ebe96423e"));
    }
}

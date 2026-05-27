package com.rentflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentBookingSnapshotParser {

    private final ObjectMapper objectMapper;

    public PaymentBookingSnapshotParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PriceSnapshot readPriceSnapshot(Booking booking) {
        JsonNode root = readTree(booking.getPriceSnapshot(), "price");
        JsonNode totalAmountNode = root.get("totalAmount");
        if (totalAmountNode == null || totalAmountNode.isNull()) {
            throw new IllegalStateException("Booking price snapshot is missing totalAmount");
        }

        String currency = root.path("currency").asText("VND");
        return new PriceSnapshot(totalAmountNode.decimalValue(), currency);
    }

    public PolicySnapshot readPolicySnapshot(Booking booking) {
        JsonNode root = readTree(booking.getPolicySnapshot(), "policy");
        return new PolicySnapshot(root.path("instantBook").asBoolean(false));
    }

    private JsonNode readTree(String json, String snapshotType) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse booking " + snapshotType + " snapshot", e);
        }
    }

    public record PriceSnapshot(
            BigDecimal totalAmount,
            String currency
    ) {
    }

    public record PolicySnapshot(
            boolean instantBook
    ) {
    }
}

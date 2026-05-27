package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CoreBankPaymentClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CoreBankPaymentClient(
            RestClient.Builder restClientBuilder,
            CoreBankPaymentProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
        this.objectMapper = objectMapper;
    }

    public CoreBankAuthorizeHoldResult authorizeHold(CoreBankAuthorizeHoldRequest request) {
        try {
            ResponseEntity<String> responseEntity = restClient.post()
                    .uri("/api/payments/authorize-hold")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            String responseBody = requiredBody(responseEntity.getBody(), "CoreBank authorize-hold response body was empty");
            return new CoreBankAuthorizeHoldResult(
                    deserialize(responseBody, CoreBankAuthorizeHoldResponse.class),
                    responseBody);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw authorizationFailed(e.getResponseBodyAsString());
            }
            throw new PaymentProviderUnavailableException(
                    "CoreBank authorize-hold request failed with status " + e.getStatusCode(), e);
        }
    }

    public CoreBankVoidHoldResult voidHold(CoreBankVoidHoldRequest request) {
        try {
            ResponseEntity<String> responseEntity = restClient.post()
                    .uri("/api/payments/void-hold")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            String responseBody = requiredBody(responseEntity.getBody(), "CoreBank void-hold response body was empty");
            return new CoreBankVoidHoldResult(
                    deserialize(responseBody, CoreBankVoidHoldResponse.class),
                    responseBody);
        } catch (RestClientResponseException e) {
            throw new PaymentProviderUnavailableException(
                    "CoreBank void-hold request failed with status " + e.getStatusCode(), e);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(CoreBankPaymentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        return requestFactory;
    }

    private <T> T deserialize(String body, Class<T> responseType) {
        try {
            return objectMapper.readValue(body, responseType);
        } catch (JsonProcessingException e) {
            throw new PaymentProviderUnavailableException("Failed to parse CoreBank response", e);
        }
    }

    private CoreBankAuthorizationFailedException authorizationFailed(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new CoreBankAuthorizationFailedException(
                    root.path("message").asText("CoreBank authorize request was declined"),
                    root.path("status").asText(null),
                    root.path("code").asText(null),
                    root.path("message").asText("CoreBank authorize request was declined"),
                    body);
        } catch (JsonProcessingException e) {
            return new CoreBankAuthorizationFailedException(
                    "CoreBank authorize request was declined",
                    null,
                    null,
                    null,
                    body);
        }
    }

    private String requiredBody(String body, String message) {
        if (body == null || body.isBlank()) {
            throw new PaymentProviderUnavailableException(message);
        }
        return body;
    }
}

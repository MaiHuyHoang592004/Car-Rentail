package com.rentflow.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.entity.AuditLog;
import com.rentflow.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultAuditLogServiceTest {

    @Test
    void recordRedactsSensitiveFieldsInDetailsJson() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        DefaultAuditLogService service = new DefaultAuditLogService(repository, new ObjectMapper());

        service.record(
                UUID.randomUUID(),
                "USER",
                "BOOKING_CANCELLED",
                "BOOKING",
                UUID.randomUUID(),
                "SUCCESS",
                """
                {
                  "tokenHash":"abc",
                  "password_hash":"xyz",
                  "plate_number_encrypted":"enc-plate",
                  "nested":{"providerPaymentOrderId":"corebank-order"},
                  "safeField":"ok"
                }
                """);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        String details = captor.getValue().getDetails();

        assertThat(details).contains("\"tokenHash\":\"[REDACTED]\"");
        assertThat(details).contains("\"password_hash\":\"[REDACTED]\"");
        assertThat(details).contains("\"plate_number_encrypted\":\"[REDACTED]\"");
        assertThat(details).contains("\"providerPaymentOrderId\":\"[REDACTED]\"");
        assertThat(details).contains("\"safeField\":\"ok\"");
    }

    @Test
    void recordKeepsDetailsWhenPayloadIsNotJson() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        DefaultAuditLogService service = new DefaultAuditLogService(repository, new ObjectMapper());
        String raw = "plain-text-details";

        service.record(
                null,
                "SYSTEM",
                "HEALTH_CHECK",
                "SYSTEM",
                null,
                "SUCCESS",
                raw);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isEqualTo(raw);
    }
}

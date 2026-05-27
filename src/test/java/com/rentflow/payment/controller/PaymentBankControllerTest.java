package com.rentflow.payment.controller;

import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.payment.dto.PaymentBankListResponse;
import com.rentflow.payment.dto.PaymentBankResponse;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.service.PaymentBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentBankControllerTest {

    private PaymentBankService paymentBankService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentBankService = mock(PaymentBankService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentBankController(paymentBankService))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void listActiveBanksReturnsContractShape() throws Exception {
        UUID coreBankId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        when(paymentBankService.listActiveBanks()).thenReturn(new PaymentBankListResponse(List.of(
                new PaymentBankResponse(
                        coreBankId,
                        "COREBANK",
                        null,
                        "CoreBank Demo Bank",
                        "CoreBank Demo Bank",
                        PaymentMethod.COREBANK_TRANSFER,
                        PaymentProviderType.COREBANK,
                        true))));

        mockMvc.perform(get("/api/v1/payment-banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(coreBankId.toString()))
                .andExpect(jsonPath("$.items[0].code").value("COREBANK"))
                .andExpect(jsonPath("$.items[0].bin").doesNotExist())
                .andExpect(jsonPath("$.items[0].shortName").value("CoreBank Demo Bank"))
                .andExpect(jsonPath("$.items[0].fullName").value("CoreBank Demo Bank"))
                .andExpect(jsonPath("$.items[0].paymentMethod").value("COREBANK_TRANSFER"))
                .andExpect(jsonPath("$.items[0].provider").value("COREBANK"))
                .andExpect(jsonPath("$.items[0].active").value(true));

        verify(paymentBankService).listActiveBanks();
    }
}

package com.rentflow.integration.payment;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.repository.PaymentBankRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class PaymentBankIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentBankRepository paymentBankRepository;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void tearDown() {
        paymentBankRepository.findAll().stream()
                .filter(bank -> "INACTIVE_TEST".equals(bank.getCode()))
                .forEach(paymentBankRepository::delete);
        authUserRepository.findAll().stream()
                .filter(user -> user.getEmail().startsWith("payment-banks-"))
                .forEach(authUserRepository::delete);
    }

    @Test
    void listPaymentBanksReturnsSeededActiveBanksAndSkipsInactiveOnes() throws Exception {
        PaymentBank inactiveBank = new PaymentBank();
        inactiveBank.setCode("INACTIVE_TEST");
        inactiveBank.setShortName("Inactive Test Bank");
        inactiveBank.setFullName("Inactive Test Bank");
        inactiveBank.setCountryCode("VN");
        inactiveBank.setPaymentMethod(PaymentMethod.BANK_TRANSFER_QR);
        inactiveBank.setProvider(PaymentProviderType.VIETQR_MANUAL);
        inactiveBank.setActive(false);
        inactiveBank.setDisplayOrder(5);
        paymentBankRepository.save(inactiveBank);

        List<PaymentBank> activeBanks = paymentBankRepository.findByActiveTrueOrderByDisplayOrderAscShortNameAsc();
        assertThat(activeBanks).extracting(PaymentBank::getCode).contains("COREBANK", "VCB");
        assertThat(activeBanks).extracting(PaymentBank::getCode).doesNotContain("INACTIVE_TEST");

        String token = token(saveCustomer());

        mockMvc.perform(get("/api/v1/payment-banks")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("VCB"))
                .andExpect(jsonPath("$.items[0].provider").value("VIETQR_MANUAL"))
                .andExpect(jsonPath("$.items[*].code").isArray())
                .andExpect(jsonPath("$.items[?(@.code == 'COREBANK')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.code != 'COREBANK')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.code == 'INACTIVE_TEST')]").isEmpty());
    }

    private AuthUser saveCustomer() {
        AuthUser user = new AuthUser(
                "payment-banks-" + UUID.randomUUID() + "@example.com",
                "hash",
                UserStatus.ACTIVE,
                true);
        user.addRole(Role.CUSTOMER);
        return authUserRepository.save(user);
    }

    private String token(AuthUser user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));
    }
}

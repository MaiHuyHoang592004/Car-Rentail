package com.rentflow.payment.repository;

import com.rentflow.payment.entity.CustomerPaymentAccount;
import com.rentflow.payment.entity.PaymentProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerPaymentAccountRepository extends JpaRepository<CustomerPaymentAccount, UUID> {

    Optional<CustomerPaymentAccount> findByUserIdAndProviderAndActiveTrue(UUID userId, PaymentProviderType provider);
}

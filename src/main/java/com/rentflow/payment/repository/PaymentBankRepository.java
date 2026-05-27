package com.rentflow.payment.repository;

import com.rentflow.payment.entity.PaymentBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentBankRepository extends JpaRepository<PaymentBank, UUID> {

    List<PaymentBank> findByActiveTrueOrderByDisplayOrderAscShortNameAsc();
}

package com.rentflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rentflow.payment.bank-transfer")
public class BankTransferProperties {

    private String accountNumber = "1234567890";
    private String accountName = "RENTFLOW ESCROW";
    private String transferContentPrefix = "RENTFLOW";

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getTransferContentPrefix() {
        return transferContentPrefix;
    }

    public void setTransferContentPrefix(String transferContentPrefix) {
        this.transferContentPrefix = transferContentPrefix;
    }
}

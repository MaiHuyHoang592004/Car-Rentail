package com.rentflow.report.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "rentflow.report")
public class ReportProperties {

    private BigDecimal platformFeeRate = new BigDecimal("0.15");

    public BigDecimal getPlatformFeeRate() {
        return platformFeeRate;
    }

    public void setPlatformFeeRate(BigDecimal platformFeeRate) {
        this.platformFeeRate = platformFeeRate;
    }
}

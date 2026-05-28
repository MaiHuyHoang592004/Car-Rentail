package com.rentflow.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void defaultsMatchSlice8BConfiguration() {
        RateLimitProperties properties = new RateLimitProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getLogin().getLimit()).isEqualTo(5);
        assertThat(properties.getLogin().getWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getBooking().getCreateLimit()).isEqualTo(10);
        assertThat(properties.getBooking().getCreateWindow()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getPublicEndpoint().getLimit()).isEqualTo(60);
        assertThat(properties.getPublicEndpoint().getWindow()).isEqualTo(Duration.ofMinutes(1));
    }
}

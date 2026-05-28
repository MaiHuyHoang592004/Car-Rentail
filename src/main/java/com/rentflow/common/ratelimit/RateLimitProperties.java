package com.rentflow.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rentflow.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Login login = new Login();
    private Booking booking = new Booking();
    private PublicEndpoint publicEndpoint = new PublicEndpoint();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public PublicEndpoint getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(PublicEndpoint publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public static class Login {
        private int limit = 5;
        private Duration window = Duration.ofMinutes(15);

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Booking {
        private int createLimit = 10;
        private Duration createWindow = Duration.ofHours(1);

        public int getCreateLimit() {
            return createLimit;
        }

        public void setCreateLimit(int createLimit) {
            this.createLimit = createLimit;
        }

        public Duration getCreateWindow() {
            return createWindow;
        }

        public void setCreateWindow(Duration createWindow) {
            this.createWindow = createWindow;
        }
    }

    public static class PublicEndpoint {
        private int limit = 60;
        private Duration window = Duration.ofMinutes(1);

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}

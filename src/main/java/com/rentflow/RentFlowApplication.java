package com.rentflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class RentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentFlowApplication.class, args);
    }
}

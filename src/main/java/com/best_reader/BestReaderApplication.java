package com.best_reader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BestReaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(BestReaderApplication.class, args);
    }
}
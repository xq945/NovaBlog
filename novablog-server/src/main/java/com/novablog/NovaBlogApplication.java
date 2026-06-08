package com.novablog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NovaBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovaBlogApplication.class, args);
    }
}

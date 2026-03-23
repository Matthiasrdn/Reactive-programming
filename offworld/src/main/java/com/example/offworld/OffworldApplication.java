package com.example.offworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.offworld.config.OffworldProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OffworldProperties.class)
public class OffworldApplication {

    public static void main(String[] args) {
        SpringApplication.run(OffworldApplication.class, args);
    }
}

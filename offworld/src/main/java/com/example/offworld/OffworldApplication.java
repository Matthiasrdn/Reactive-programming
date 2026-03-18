package com.example.offworld;

import com.example.offworld.config.OffworldProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OffworldProperties.class)
public class OffworldApplication {

    public static void main(String[] args) {
        SpringApplication.run(OffworldApplication.class, args);
    }
}
package com.example.offworld.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.offworld.dto.station.StationDto;

import reactor.core.publisher.Mono;

@Component
public class StationClient {

    private final WebClient webClient;

    public StationClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<StationDto> getStation(String systemName, String planetId) {
        return webClient.get()
                .uri("/settlements/{systemName}/{planetId}/station", systemName, planetId)
                .retrieve()
                .bodyToMono(StationDto.class);
    }
}

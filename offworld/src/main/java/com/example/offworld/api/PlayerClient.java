package com.example.offworld.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.offworld.dto.player.UpdatePlayerRequest;

import reactor.core.publisher.Mono;

@Component
public class PlayerClient {

    private final WebClient webClient;

    public PlayerClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Void> updateCallbackUrl(String playerId, String callbackUrl) {
        return webClient.put()
                .uri("/players/{id}", playerId)
                .bodyValue(new UpdatePlayerRequest(callbackUrl))
                .retrieve()
                .bodyToMono(Void.class);
    }
}

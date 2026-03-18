package com.example.offworld.api;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.offworld.dto.market.TradeEvent;

import reactor.core.publisher.Flux;

@Component
public class MarketClient {

    private final WebClient webClient;

    public MarketClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<TradeEvent> streamTrades() {
        return webClient.get()
                .uri("/market/trades")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<TradeEvent>>() {
                })
                .mapNotNull(ServerSentEvent::data);
    }
}

package com.example.offworld.api;

import com.example.offworld.dto.market.CreateOrderRequest;
import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.TradeEvent;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<TradeEvent>>() {
                })
                .mapNotNull(ServerSentEvent::data);
    }

    public Mono<Map<String, Integer>> getPrices() {
        return webClient.get()
                .uri("/market/prices")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Integer>>() {
                });
    }

    public Mono<OrderBookDto> getOrderBook(String goodName) {
        return webClient.get()
                .uri("/market/book/{goodName}", goodName)
                .retrieve()
                .bodyToMono(OrderBookDto.class);
    }

    public Mono<MarketOrderDto> createLimitBuyOrder(String goodName, int price, int quantity, String stationPlanetId) {
        return createOrder(new CreateOrderRequest(
                goodName,
                "buy",
                "limit",
                price,
                quantity,
                stationPlanetId
        ));
    }

    public Mono<MarketOrderDto> createLimitSellOrder(String goodName, int price, int quantity, String stationPlanetId) {
        return createOrder(new CreateOrderRequest(
                goodName,
                "sell",
                "limit",
                price,
                quantity,
                stationPlanetId
        ));
    }

    public Mono<MarketOrderDto> createOrder(CreateOrderRequest request) {
        return webClient.post()
                .uri("/market/orders")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MarketOrderDto.class);
    }

    public Flux<MarketOrderDto> getMyOrders() {
        return webClient.get()
                .uri("/market/orders")
                .retrieve()
                .bodyToFlux(MarketOrderDto.class);
    }

    public Mono<Void> cancelOrder(String orderId) {
        return webClient.delete()
                .uri("/market/orders/{id}", orderId)
                .retrieve()
                .bodyToMono(Void.class);
    }
}

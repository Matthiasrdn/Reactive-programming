package com.example.offworld.api;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.offworld.dto.shipping.AuthorizationRequest;
import com.example.offworld.dto.shipping.ShipDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ShippingClient {

    private final WebClient webClient;

    public ShippingClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<ShipDto> listShips() {
        return webClient.get()
                .uri("/ships")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ShipDto>>() {
                })
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<ShipDto> getShip(String shipId) {
        return webClient.get()
                .uri("/ships/{id}", shipId)
                .retrieve()
                .bodyToMono(ShipDto.class);
    }

    public Mono<ShipDto> authorizeDock(String shipId) {
        return webClient.put()
                .uri("/ships/{id}/dock", shipId)
                .bodyValue(new AuthorizationRequest(true))
                .retrieve()
                .bodyToMono(ShipDto.class);
    }

    public Mono<ShipDto> authorizeUndock(String shipId) {
        return webClient.put()
                .uri("/ships/{id}/undock", shipId)
                .bodyValue(new AuthorizationRequest(true))
                .retrieve()
                .bodyToMono(ShipDto.class);
    }

    public Mono<ShipDto> createTrucking(String originPlanetId, String destinationPlanetId, java.util.Map<String, Integer> cargo) {
        return webClient.post()
                .uri("/trucking")
                .bodyValue(new com.example.offworld.dto.shipping.CreateTruckingRequest(
                        originPlanetId,
                        destinationPlanetId,
                        cargo
                ))
                .retrieve()
                .bodyToMono(ShipDto.class);
    }
}

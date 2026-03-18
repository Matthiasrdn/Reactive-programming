package com.example.offworld.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.ShippingClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.webhook.WebhookEvent;
import com.example.offworld.support.ApiException;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ShipAutomationService {

    private static final Logger log = LoggerFactory.getLogger(ShipAutomationService.class);

    private final ShippingClient shippingClient;
    private final OffworldProperties props;

    public ShipAutomationService(ShippingClient shippingClient, OffworldProperties props) {
        this.shippingClient = shippingClient;
        this.props = props;
    }

    @PostConstruct
    public void startPolling() {
        Flux.interval(props.getPolling().getShipInterval())
                .flatMap(tick -> shippingClient.listShips())
                .flatMap(ship -> processShip(ship.id()))
                .onErrorContinue((error, value)
                        -> log.warn("Erreur polling ship {}: {}", value, error.getMessage()))
                .subscribe();
    }

    public Mono<Void> onWebhook(WebhookEvent event) {
        if (event.shipId() == null) {
            return Mono.empty();
        }
        return processShip(event.shipId()).then();
    }

    private Mono<ShipDto> processShip(String shipId) {
        return shippingClient.getShip(shipId)
                .flatMap(ship -> switch (ship.status()) {
            case "awaiting_origin_docking_auth", "awaiting_docking_auth" ->
                shippingClient.authorizeDock(ship.id())
                .doOnSuccess(s -> log.info("Dock autorisé pour {}", s.id()));

            case "awaiting_origin_undocking_auth", "awaiting_undocking_auth" ->
                shippingClient.authorizeUndock(ship.id())
                .doOnSuccess(s -> log.info("Undock autorisé pour {}", s.id()));

            default ->
                Mono.just(ship);
        })
                .onErrorResume(ApiException.class, ex -> {
                    if (ex.getStatus() == 503) {
                        log.warn("Pas de docking bay dispo pour {}. Retry plus tard.", shipId);
                        return Mono.empty();
                    }
                    log.warn("Erreur ship {} -> {} {}", shipId, ex.getStatus(), ex.getMessage());
                    return Mono.empty();
                });
    }
}

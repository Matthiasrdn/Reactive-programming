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
    private final DebugStateService debugStateService;
    private final DebugShipService debugShipService;

    public ShipAutomationService(
            ShippingClient shippingClient,
            OffworldProperties props,
            DebugStateService debugStateService,
            DebugShipService debugShipService
    ) {
        this.shippingClient = shippingClient;
        this.props = props;
        this.debugStateService = debugStateService;
        this.debugShipService = debugShipService;
    }

    @PostConstruct
    public void startPolling() {
        debugStateService.recordShipAction("Ship polling démarré");

        Flux.interval(props.getPolling().getShipInterval())
                .doOnNext(tick -> debugStateService.recordShipAction("Polling ships"))
                .flatMap(tick -> shippingClient.listShips())
                .filter(ship -> props.getPlayerId().equals(ship.ownerId()))
                .flatMap(ship -> processShip(ship.id()))
                .onErrorContinue((error, value) -> {
                    String message = "Erreur polling ship " + value + ": " + error.getMessage();
                    debugStateService.recordShipAction(message);
                    log.warn(message);
                })
                .subscribe();
    }

    public Mono<Void> onWebhook(WebhookEvent event) {
        if (event.shipId() == null) {
            return Mono.empty();
        }

        debugStateService.recordShipAction("Webhook ship reçu pour " + event.shipId());
        debugShipService.recordShipNote(event.shipId(), "Webhook reçu: " + event.event());
        return processShip(event.shipId()).then();
    }

    private Mono<ShipDto> processShip(String shipId) {
        return shippingClient.getShip(shipId)
                .doOnNext(ship -> {
                    debugShipService.recordShipSnapshot(ship, "Ship observé par polling");
                    debugStateService.recordShipAction("Ship " + ship.id() + " status=" + ship.status());
                })
                .flatMap(ship -> switch (ship.status()) {
            case "awaiting_origin_docking_auth" ->
                shippingClient.authorizeDock(ship.id())
                .doOnSuccess(s -> {
                    debugStateService.recordShipAction("Origin dock autorisé pour " + s.id());
                    debugShipService.recordShipSnapshot(s, "Origin dock autorisé");
                    log.info("Origin dock autorisé pour {}", s.id());
                });

            case "awaiting_origin_undocking_auth" ->
                shippingClient.authorizeUndock(ship.id())
                .doOnSuccess(s -> {
                    debugStateService.recordShipAction("Origin undock autorisé pour " + s.id());
                    debugShipService.recordShipSnapshot(s, "Origin undock autorisé");
                    log.info("Origin undock autorisé pour {}", s.id());
                });

            case "awaiting_docking_auth" ->
                shippingClient.authorizeDock(ship.id())
                .doOnSuccess(s -> {
                    debugStateService.recordShipAction("Dock destination autorisé pour " + s.id());
                    debugShipService.recordShipSnapshot(s, "Dock destination autorisé");
                    log.info("Dock destination autorisé pour {}", s.id());
                });

            case "awaiting_undocking_auth" ->
                shippingClient.authorizeUndock(ship.id())
                .doOnSuccess(s -> {
                    debugStateService.recordShipAction("Undock destination autorisé pour " + s.id());
                    debugShipService.recordShipSnapshot(s, "Undock destination autorisé");
                    log.info("Undock destination autorisé pour {}", s.id());
                });

            default ->
                Mono.just(ship);
        })
                .onErrorResume(ApiException.class, ex -> {
                    String msg;

                    if (ex.getStatus() == 503) {
                        msg = "Pas de docking bay dispo pour " + shipId;
                    } else if (ex.getStatus() == 403) {
                        msg = "Ship ignoré (pas propriétaire station destination/origine) " + shipId;
                    } else {
                        msg = "Erreur ship " + shipId + " -> " + ex.getStatus() + " " + ex.getMessage();
                    }

                    debugStateService.recordShipAction(msg);
                    debugShipService.recordShipNote(shipId, msg);
                    log.warn(msg);
                    return Mono.empty();
                });
    }
}

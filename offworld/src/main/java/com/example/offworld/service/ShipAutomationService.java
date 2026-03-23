package com.example.offworld.service;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.offworld.api.ShippingClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.webhook.WebhookEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ShipAutomationService {

    private static final Logger log = LoggerFactory.getLogger(ShipAutomationService.class);

    private static final Set<String> ACTIONABLE_STATUSES = Set.of(
            "awaiting_docking_auth",
            "awaiting_undocking_auth"
    );

    private final ShippingClient shippingClient;
    private final OffworldProperties props;
    private final SimulationStateService simulationStateService;
    private final DebugShipService debugShipService;
    private final DebugStateService debugStateService;

    public ShipAutomationService(
            ShippingClient shippingClient,
            OffworldProperties props,
            SimulationStateService simulationStateService,
            DebugShipService debugShipService,
            DebugStateService debugStateService
    ) {
        this.shippingClient = shippingClient;
        this.props = props;
        this.simulationStateService = simulationStateService;
        this.debugShipService = debugShipService;
        this.debugStateService = debugStateService;
    }

    @Scheduled(initialDelay = 1500, fixedDelayString = "${offworld.sync.ships-interval-ms:5000}")
    public void runAutomation() {
        processShips().then().subscribe();
    }

    public Flux<ShipDto> processShips() {
        return shippingClient.listShips()
                .filter(this::isOwnedShip)
                .filter(this::isRelevantShip)
                .filter(this::isActionableShip)
                .flatMap(this::handleShip)
                .onErrorResume(error -> {
                    log.debug("Ship processing skipped", error);
                    return Flux.empty();
                });
    }

    private boolean isRelevantShip(ShipDto ship) {
        if (ship == null) {
            return false;
        }
        return isOwnedStation(ship.originPlanetId()) || isOwnedStation(ship.destinationPlanetId());
    }

    private boolean isActionableShip(ShipDto ship) {
        String status = normalize(ship.status());
        return ACTIONABLE_STATUSES.contains(status);
    }

    public Mono<Void> onWebhook(WebhookEvent event) {
        if (event == null) {
            return Mono.empty();
        }

        return processShips().then();
    }

    public Mono<ShipDto> handleShip(ShipDto ship) {
        if (ship == null || ship.id() == null || ship.id().isBlank()) {
            return Mono.empty();
        }

        if (!isOwnedShip(ship)) {
            return Mono.empty();
        }

        simulationStateService.updateShip(ship, "ship-automation:seen");
        debugShipService.recordShipSnapshot(ship, "ship-automation:seen");

        String status = normalize(ship.status());
        String originPlanetId = ship.originPlanetId();
        String destinationPlanetId = ship.destinationPlanetId();

        boolean canUseOrigin = isOwnedStation(originPlanetId);
        boolean canUseDestination = isOwnedStation(destinationPlanetId);

        if (status.equals("awaiting_docking_auth")) {
            if (!canUseDestination) {
                return Mono.just(ship);
            }

            debugStateService.recordShipAction("authorize-dock:" + ship.id());
            return shippingClient.authorizeDock(ship.id())
                    .doOnNext(updated -> {
                        simulationStateService.updateShip(updated, "ship-automation:dock");
                        debugShipService.recordShipSnapshot(updated, "ship-automation:dock");
                        log.info("Dock autorisé pour ship {}", updated.id());
                    })
                    .onErrorResume(error -> {
                        log.debug("Dock non autorisé pour ship {}", ship.id(), error);
                        return Mono.just(ship);
                    });
        }

        if (status.equals("awaiting_undocking_auth")) {
            if (!canUseOrigin) {
                return Mono.just(ship);
            }

            debugStateService.recordShipAction("authorize-undock:" + ship.id());
            return shippingClient.authorizeUndock(ship.id())
                    .doOnNext(updated -> {
                        simulationStateService.updateShip(updated, "ship-automation:undock");
                        debugShipService.recordShipSnapshot(updated, "ship-automation:undock");
                        log.info("Undock autorisé pour ship {}", updated.id());
                    })
                    .onErrorResume(error -> {
                        log.debug("Undock non autorisé pour ship {}", ship.id(), error);
                        return Mono.just(ship);
                    });
        }

        return Mono.just(ship);
    }

    private boolean isOwnedShip(ShipDto ship) {
        return ship != null
                && ship.ownerId() != null
                && ship.ownerId().equals(props.getPlayerId());
    }

    private boolean isOwnedStation(String planetId) {
        if (planetId == null || planetId.isBlank()) {
            return false;
        }

        Map<String, SimulationStateService.PlanetState> knownPlanets = simulationStateService.planetStates();
        return knownPlanets.containsKey(planetId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}

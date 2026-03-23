package com.example.offworld.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.api.ShippingClient;
import com.example.offworld.api.StationClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.support.ApiException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StateSyncService {

    private static final Logger log = LoggerFactory.getLogger(StateSyncService.class);

    private final StationClient stationClient;
    private final ShippingClient shippingClient;
    private final MarketClient marketClient;
    private final OffworldProperties props;
    private final SimulationStateService simulationStateService;
    private final DebugStateService debugStateService;
    private final DebugShipService debugShipService;

    private final Set<String> lastOpenOrderIds = ConcurrentHashMap.newKeySet();
    private final Set<String> inaccessibleStations = ConcurrentHashMap.newKeySet();

    public StateSyncService(
            StationClient stationClient,
            ShippingClient shippingClient,
            MarketClient marketClient,
            OffworldProperties props,
            SimulationStateService simulationStateService,
            DebugStateService debugStateService,
            DebugShipService debugShipService
    ) {
        this.stationClient = stationClient;
        this.shippingClient = shippingClient;
        this.marketClient = marketClient;
        this.props = props;
        this.simulationStateService = simulationStateService;
        this.debugStateService = debugStateService;
        this.debugShipService = debugShipService;
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${offworld.sync.stations-interval-ms:5000}")
    public void syncStations() {
        List<StationTarget> targets = buildStationTargets();

        Flux.fromIterable(targets)
                .filter(target -> !inaccessibleStations.contains(target.key()))
                .flatMap(target
                        -> stationClient.getStation(target.systemName(), target.planetId())
                        .doOnNext(station -> {
                            simulationStateService.updatePlanetInventory(
                                    target.systemName(),
                                    target.planetId(),
                                    station,
                                    "sync:station"
                            );
                            debugStateService.recordLogisticsAction(
                                    "station-sync:" + target.systemName() + "/" + target.planetId()
                            );
                        })
                        .onErrorResume(error -> {
                            if (isForbiddenOrNotFound(error)) {
                                inaccessibleStations.add(target.key());
                                debugStateService.recordLogisticsAction(
                                        "station-skip:" + target.systemName() + "/" + target.planetId()
                                );
                                log.debug("Skipping inaccessible station {}/{}", target.systemName(), target.planetId());
                                return Mono.empty();
                            }

                            log.warn("Station sync failed for {}/{}", target.systemName(), target.planetId(), error);
                            return Mono.empty();
                        })
                )
                .then()
                .subscribe();
    }

    @Scheduled(initialDelay = 1500, fixedDelayString = "${offworld.sync.ships-interval-ms:5000}")
    public void syncShips() {
        shippingClient.listShips()
                .filter(ship -> props.getPlayerId().equals(ship.ownerId()))
                .flatMap(this::updateAndMaybeAuthorizeShip)
                .then()
                .subscribe();
    }

    @Scheduled(initialDelay = 2000, fixedDelayString = "${offworld.sync.orders-interval-ms:5000}")
    public void syncOrders() {
        marketClient.getMyOrders()
                .collectList()
                .doOnNext(orders -> {
                    Set<String> currentOpen = new LinkedHashSet<>();

                    for (MarketOrderDto order : orders) {
                        simulationStateService.updateOrder(order, "sync:orders");
                        if (!isTerminalOrder(order.status())) {
                            currentOpen.add(order.id());
                        }
                    }

                    for (String previousId : new ArrayList<>(lastOpenOrderIds)) {
                        if (!currentOpen.contains(previousId)) {
                            simulationStateService.removeOrder(previousId, "sync:orders");
                        }
                    }

                    lastOpenOrderIds.clear();
                    lastOpenOrderIds.addAll(currentOpen);
                })
                .onErrorResume(error -> {
                    log.warn("Order sync failed", error);
                    return Mono.empty();
                })
                .subscribe();
    }

    @Scheduled(initialDelay = 3000, fixedDelayString = "${offworld.sync.market-interval-ms:15000}")
    public void syncOrderBooks() {
        Set<String> goods = new LinkedHashSet<>(props.getTrading().getWatchedGoods());
        if (props.getLogistics().getGoodName() != null && !props.getLogistics().getGoodName().isBlank()) {
            goods.add(props.getLogistics().getGoodName());
        }

        simulationStateService.activeOrderStates()
                .values()
                .forEach(order -> goods.add(order.goodName()));

        Flux.fromIterable(goods)
                .flatMap(good
                        -> marketClient.getOrderBook(good)
                        .doOnNext(book -> simulationStateService.updateOrderBook(book, "sync:order-book"))
                        .onErrorResume(error -> {
                            log.debug("Order book sync skipped for {}", good, error);
                            return Mono.empty();
                        })
                )
                .then()
                .subscribe();
    }

    private Mono<ShipDto> updateAndMaybeAuthorizeShip(ShipDto ship) {
        simulationStateService.updateShip(ship, "sync:ship");
        debugShipService.recordShipSnapshot(ship, "sync");

        String status = ship.status() == null ? "" : ship.status().toLowerCase();

        if (status.contains("awaiting_docking_auth")) {
            if (!canAuthorizeDock(ship)) {
                debugStateService.recordShipAction("skip-dock-not-owner:" + ship.id());
                return Mono.just(ship);
            }

            debugStateService.recordShipAction("authorize-dock:" + ship.id());
            return shippingClient.authorizeDock(ship.id())
                    .doOnNext(updated -> {
                        simulationStateService.updateShip(updated, "sync:authorize-dock");
                        debugShipService.recordShipSnapshot(updated, "authorize-dock");
                    })
                    .onErrorResume(error -> {
                        log.debug("Dock authorization failed for {}", ship.id(), error);
                        return Mono.just(ship);
                    });
        }

        if (status.contains("awaiting_undocking_auth")) {
            if (!canAuthorizeUndock(ship)) {
                debugStateService.recordShipAction("skip-undock-not-owner:" + ship.id());
                return Mono.just(ship);
            }

            debugStateService.recordShipAction("authorize-undock:" + ship.id());
            return shippingClient.authorizeUndock(ship.id())
                    .doOnNext(updated -> {
                        simulationStateService.updateShip(updated, "sync:authorize-undock");
                        debugShipService.recordShipSnapshot(updated, "authorize-undock");
                    })
                    .onErrorResume(error -> {
                        log.debug("Undock authorization failed for {}", ship.id(), error);
                        return Mono.just(ship);
                    });
        }

        return Mono.just(ship);
    }

    private boolean canAuthorizeDock(ShipDto ship) {
        String destinationPlanetId = ship.destinationPlanetId();
        if (destinationPlanetId == null || destinationPlanetId.isBlank()) {
            return false;
        }
        return isOwnedPlanet(destinationPlanetId);
    }

    private boolean canAuthorizeUndock(ShipDto ship) {
        String originPlanetId = ship.originPlanetId();
        if (originPlanetId == null || originPlanetId.isBlank()) {
            return false;
        }
        return isOwnedPlanet(originPlanetId);
    }

    private boolean isOwnedPlanet(String planetId) {
        return simulationStateService.planetStates().containsKey(planetId);
    }

    private boolean isForbiddenOrNotFound(Throwable error) {
        if (!(error instanceof ApiException apiException)) {
            return false;
        }

        String message = apiException.getMessage();
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("forbidden")
                || lower.contains("not found")
                || lower.contains("planet not found")
                || lower.contains("you do not have permission");
    }

    private List<StationTarget> buildStationTargets() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        List<StationTarget> targets = new ArrayList<>();

        addTarget(
                targets,
                unique,
                props.getTrading().getStationSystemName(),
                props.getTrading().getStationPlanetId()
        );

        if (props.getLogistics().isEnabled()) {
            addTarget(
                    targets,
                    unique,
                    props.getLogistics().getOriginSystemName(),
                    props.getLogistics().getOriginPlanetId()
            );

            addTarget(
                    targets,
                    unique,
                    props.getLogistics().getDestinationSystemName(),
                    props.getLogistics().getDestinationPlanetId()
            );
        }

        return targets;
    }

    private void addTarget(List<StationTarget> targets, Set<String> unique, String systemName, String planetId) {
        if (systemName == null || systemName.isBlank() || planetId == null || planetId.isBlank()) {
            return;
        }

        String key = systemName + "::" + planetId;
        if (unique.add(key)) {
            targets.add(new StationTarget(systemName, planetId));
        }
    }

    private boolean isTerminalOrder(String status) {
        if (status == null) {
            return false;
        }
        return status.equalsIgnoreCase("filled")
                || status.equalsIgnoreCase("cancelled")
                || status.equalsIgnoreCase("rejected");
    }

    private record StationTarget(String systemName, String planetId) {

        String key() {
            return systemName + "::" + planetId;
        }
    }
}

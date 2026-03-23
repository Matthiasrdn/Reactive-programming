package com.example.offworld.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.offworld.api.ShippingClient;
import com.example.offworld.api.StationClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.shipping.ShipDto;

import reactor.core.publisher.Mono;

@Service
public class SimpleLogisticsAutomationService {

    private static final Logger log = LoggerFactory.getLogger(SimpleLogisticsAutomationService.class);

    private final ShippingClient shippingClient;
    private final StationClient stationClient;
    private final OffworldProperties props;
    private final SimulationStateService simulationStateService;
    private final DebugStateService debugStateService;

    public SimpleLogisticsAutomationService(
            ShippingClient shippingClient,
            StationClient stationClient,
            OffworldProperties props,
            SimulationStateService simulationStateService,
            DebugStateService debugStateService
    ) {
        this.shippingClient = shippingClient;
        this.stationClient = stationClient;
        this.props = props;
        this.simulationStateService = simulationStateService;
        this.debugStateService = debugStateService;
    }

    @Scheduled(initialDelay = 5000, fixedDelayString = "${offworld.sync.logistics-interval-ms:8000}")
    public void maybeLaunchTrucking() {
        if (!props.getLogistics().isEnabled()) {
            return;
        }

        String systemName = props.getLogistics().getOriginSystemName();
        String originPlanetId = props.getLogistics().getOriginPlanetId();
        String destinationPlanetId = props.getLogistics().getDestinationPlanetId();
        String goodName = props.getLogistics().getGoodName();
        int quantity = props.getLogistics().getQuantity();

        if (originPlanetId.equals(destinationPlanetId)) {
            debugStateService.recordLogisticsAction("skip-same-station-route");
            return;
        }

        stationClient.getStation(systemName, originPlanetId)
                .flatMap(originStation -> {
                    simulationStateService.updatePlanetInventory(
                            systemName,
                            originPlanetId,
                            originStation,
                            "logistics:origin-station"
                    );

                    int stock = originStation.inventory() == null ? 0 : originStation.inventory().getOrDefault(goodName, 0);

                    if (stock < quantity) {
                        debugStateService.recordLogisticsAction(
                                "waiting-stock:" + goodName + " stock=" + stock + " need=" + quantity
                        );
                        return Mono.empty();
                    }

                    return shippingClient.listShips()
                            .filter(ship -> props.getPlayerId().equals(ship.ownerId()))
                            .collectList()
                            .flatMap(existingShips -> {
                                if (hasActiveShipment(existingShips, originPlanetId, destinationPlanetId, goodName)) {
                                    debugStateService.recordLogisticsAction("shipment-already-active:" + goodName);
                                    return Mono.empty();
                                }

                                debugStateService.recordLogisticsAction(
                                        "launch-trucking:" + originPlanetId + "->" + destinationPlanetId + " " + goodName
                                );

                                return shippingClient.createTrucking(
                                        originPlanetId,
                                        destinationPlanetId,
                                        Map.of(goodName, quantity)
                                )
                                        .doOnNext(ship -> simulationStateService.updateShip(ship, "logistics:create-trucking"))
                                        .then();
                            });
                })
                .onErrorResume(error -> {
                    log.warn("Logistics launch failed", error);
                    return Mono.empty();
                })
                .subscribe();
    }

    private boolean hasActiveShipment(
            List<ShipDto> ships,
            String originPlanetId,
            String destinationPlanetId,
            String goodName
    ) {
        for (ShipDto ship : ships) {
            boolean sameRoute = originPlanetId.equals(ship.originPlanetId())
                    && destinationPlanetId.equals(ship.destinationPlanetId());

            boolean sameCargo = ship.cargo() != null
                    && ship.cargo().getOrDefault(goodName, 0) > 0;

            boolean active = !isTerminalShipStatus(ship.status());

            if (sameRoute && sameCargo && active) {
                return true;
            }
        }
        return false;
    }

    private boolean isTerminalShipStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toLowerCase();
        return s.contains("complete")
                || s.contains("completed")
                || s.contains("cancelled")
                || s.contains("failed");
    }
}

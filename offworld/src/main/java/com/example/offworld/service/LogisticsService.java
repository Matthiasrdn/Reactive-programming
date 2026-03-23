package com.example.offworld.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.ShippingClient;
import com.example.offworld.api.StationClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.station.StationDto;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    private final ShippingClient shippingClient;
    private final StationClient stationClient;
    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final DebugShipService debugShipService;
    private final SimulationStateService simulationStateService;

    private final AtomicBoolean truckingInFlight = new AtomicBoolean(false);

    public LogisticsService(
            ShippingClient shippingClient,
            StationClient stationClient,
            OffworldProperties props,
            DebugStateService debugStateService,
            DebugShipService debugShipService,
            SimulationStateService simulationStateService
    ) {
        this.shippingClient = shippingClient;
        this.stationClient = stationClient;
        this.props = props;
        this.debugStateService = debugStateService;
        this.debugShipService = debugShipService;
        this.simulationStateService = simulationStateService;
    }

    @PostConstruct
    public void start() {
        if (!props.getLogistics().isEnabled()) {
            debugStateService.recordLogisticsAction("Logistics désactivé");
            log.info("Logistics désactivé");
            return;
        }

        debugStateService.recordLogisticsAction("Logistics démarré");
        log.info("Logistics démarré");

        Flux.interval(Duration.ofSeconds(15))
                .doOnNext(tick
                        -> debugStateService.recordLogisticsAction("Tick logistics → vérification")
                )
                .flatMap(tick -> maybeLaunchTrucking())
                .onErrorContinue((error, value) -> {
                    String msg = "Erreur logistics: " + error.getMessage();
                    debugStateService.recordLogisticsAction(msg);
                    log.warn(msg);
                })
                .subscribe();
    }

    public Mono<Void> forceLaunchNow() {
        debugStateService.recordLogisticsAction("Lancement manuel demandé");
        return maybeLaunchTrucking();
    }

    private Mono<Void> maybeLaunchTrucking() {
        if (!truckingInFlight.compareAndSet(false, true)) {
            debugStateService.recordLogisticsAction("Trucking déjà en cours");
            return Mono.empty();
        }

        String systemName = props.getLogistics().getOriginSystemName();
        String originPlanetId = props.getLogistics().getOriginPlanetId();
        String destinationPlanetId = props.getLogistics().getDestinationPlanetId();
        String goodName = props.getLogistics().getGoodName();
        int quantity = props.getLogistics().getQuantity();

        debugStateService.recordLogisticsAction(
                "Lecture station " + originPlanetId + " pour " + goodName
        );

        return stationClient.getStation(systemName, originPlanetId)
                .doOnNext(station -> simulationStateService.updatePlanetInventory(
                        systemName,
                        originPlanetId,
                        station,
                        "logistics-poll"
                ))
                .flatMap(station -> launchIfEnoughStock(
                station,
                originPlanetId,
                destinationPlanetId,
                goodName,
                quantity
        ))
                .doFinally(signal -> truckingInFlight.set(false))
                .then();
    }

    private Mono<Void> launchIfEnoughStock(
            StationDto station,
            String originPlanetId,
            String destinationPlanetId,
            String goodName,
            int quantity
    ) {
        int stock = station.inventory().getOrDefault(goodName, 0);

        if (stock < quantity) {
            String msg = "Stock insuffisant pour " + goodName
                    + " (have=" + stock + ", need=" + quantity + ")";
            debugStateService.recordLogisticsAction(msg);
            log.info(msg);
            return Mono.empty();
        }

        debugStateService.recordLogisticsAction(
                "Stock OK → lancement trucking " + goodName
        );

        return shippingClient.createTrucking(
                originPlanetId,
                destinationPlanetId,
                Map.of(goodName, quantity)
        )
                .doOnSuccess(ship -> {
                    simulationStateService.updateShip(ship, "logistics-create-trucking");
                    String msg = "🚀 Truck lancé : " + ship.id()
                            + " | " + ship.originPlanetId()
                            + " -> " + ship.destinationPlanetId()
                            + " | cargo=" + ship.cargo()
                            + " | status=" + ship.status();

                    debugStateService.recordLogisticsAction(msg);
                    debugShipService.recordShipSnapshot(ship, "Ship créé par logistics");
                    log.info("{} fee={}", msg, ship.fee());
                })
                .then();
    }
}

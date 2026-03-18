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

    private final AtomicBoolean truckingInFlight = new AtomicBoolean(false);

    public LogisticsService(
            ShippingClient shippingClient,
            StationClient stationClient,
            OffworldProperties props
    ) {
        this.shippingClient = shippingClient;
        this.stationClient = stationClient;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        if (!props.getLogistics().isEnabled()) {
            log.info("Logistics désactivé");
            return;
        }

        Flux.interval(Duration.ofSeconds(20))
                .flatMap(tick -> maybeLaunchTrucking())
                .onErrorContinue((error, value)
                        -> log.warn("Erreur logistics: {}", error.getMessage()))
                .subscribe();
    }

    private Mono<Void> maybeLaunchTrucking() {
        if (!truckingInFlight.compareAndSet(false, true)) {
            return Mono.empty();
        }

        String systemName = props.getLogistics().getOriginSystemName();
        String originPlanetId = props.getLogistics().getOriginPlanetId();
        String destinationPlanetId = props.getLogistics().getDestinationPlanetId();
        String goodName = props.getLogistics().getGoodName();
        int quantity = props.getLogistics().getQuantity();

        return stationClient.getStation(systemName, originPlanetId)
                .flatMap(station -> launchIfEnoughStock(
                station, originPlanetId, destinationPlanetId, goodName, quantity
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
            log.info("Stock insuffisant pour trucking {}: have={}, need={}", goodName, stock, quantity);
            return Mono.empty();
        }

        return shippingClient.createTrucking(
                originPlanetId,
                destinationPlanetId,
                Map.of(goodName, quantity)
        )
                .doOnSuccess(ship -> log.info(
                "Trucking lancé ship={} {} -> {} cargo={} fee={}",
                ship.id(),
                ship.originPlanetId(),
                ship.destinationPlanetId(),
                ship.cargo(),
                ship.fee()
        ))
                .then();
    }
}

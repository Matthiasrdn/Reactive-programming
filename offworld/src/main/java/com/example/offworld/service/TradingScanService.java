package com.example.offworld.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.api.PlayerClient;
import com.example.offworld.api.StationClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.OrderBookLevel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TradingScanService {

    private static final Logger log = LoggerFactory.getLogger(TradingScanService.class);

    private final MarketClient marketClient;
    private final PlayerClient playerClient;
    private final StationClient stationClient;
    private final OrderManagementService orderManagementService;
    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final SimulationStateService simulationStateService;

    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public TradingScanService(
            MarketClient marketClient,
            PlayerClient playerClient,
            StationClient stationClient,
            OrderManagementService orderManagementService,
            OffworldProperties props,
            DebugStateService debugStateService,
            SimulationStateService simulationStateService
    ) {
        this.marketClient = marketClient;
        this.playerClient = playerClient;
        this.stationClient = stationClient;
        this.orderManagementService = orderManagementService;
        this.props = props;
        this.debugStateService = debugStateService;
        this.simulationStateService = simulationStateService;
    }

    @Scheduled(initialDelay = 4000, fixedDelayString = "${offworld.sync.trading-scan-interval-ms:15000}")
    public void scheduledScan() {
        if (!props.getTrading().isEnabled()) {
            return;
        }

        Set<String> goods = new LinkedHashSet<>(props.getTrading().getWatchedGoods());
        if (props.getLogistics().getGoodName() != null && !props.getLogistics().getGoodName().isBlank()) {
            goods.add(props.getLogistics().getGoodName());
        }

        Flux.fromIterable(goods)
                .filter(good -> good != null && !good.isBlank())
                .filter(inProgress::add)
                .concatMap(this::evaluateGood)
                .subscribe();
    }

    private Mono<Void> evaluateGood(String goodName) {
        String systemName = props.getTrading().getStationSystemName();
        String stationPlanetId = props.getTrading().getStationPlanetId();
        int quantity = props.getTrading().getDefaultQuantity();
        int minSpread = props.getTrading().getMinSpread();

        return marketClient.getOrderBook(goodName)
                .doOnNext(book -> simulationStateService.updateOrderBook(book, "trading-scan:order-book"))
                .flatMap(book -> {
                    OrderBookLevel bestBid = firstLevel(book.bids());
                    OrderBookLevel bestAsk = firstLevel(book.asks());

                    if (bestBid == null || bestAsk == null) {
                        return Mono.empty();
                    }

                    int spread = bestAsk.price() - bestBid.price();

                    return Mono.zip(
                            playerClient.getPlayer(props.getPlayerId()),
                            stationClient.getStation(systemName, stationPlanetId)
                                    .doOnNext(station -> simulationStateService.updatePlanetInventory(
                                    systemName,
                                    stationPlanetId,
                                    station,
                                    "trading-scan:station"
                            ))
                    )
                            .flatMap(tuple -> {
                                var player = tuple.getT1();
                                var station = tuple.getT2();

                                int credits = player.credits();
                                int stock = station.inventory() == null ? 0 : station.inventory().getOrDefault(goodName, 0);

                                if (spread >= minSpread) {
                                    int buyPrice = bestBid.price() + 1;
                                    int cost = buyPrice * quantity;

                                    if (credits >= cost) {
                                        debugStateService.recordTradeAction("scan-buy:" + goodName);
                                        return orderManagementService
                                                .placeAndMonitorBuyOrder(goodName, buyPrice, quantity, stationPlanetId)
                                                .then();
                                    }
                                }

                                if (stock >= quantity) {
                                    int sellPrice = bestBid.price();
                                    debugStateService.recordTradeAction("scan-sell:" + goodName);
                                    return orderManagementService
                                            .placeAndMonitorSellOrder(goodName, sellPrice, quantity, stationPlanetId)
                                            .then();
                                }

                                debugStateService.recordTradeAction("scan-none:" + goodName);
                                return Mono.empty();
                            });
                })
                .onErrorResume(error -> {
                    log.warn("Trading scan failed for {}", goodName, error);
                    return Mono.empty();
                })
                .doFinally(signal -> inProgress.remove(goodName));
    }

    private OrderBookLevel firstLevel(java.util.List<OrderBookLevel> levels) {
        return levels == null || levels.isEmpty() ? null : levels.get(0);
    }
}

package com.example.offworld.service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.api.PlayerClient;
import com.example.offworld.api.StationClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.OrderBookLevel;
import com.example.offworld.dto.market.TradeEvent;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class AutoTradingService {

    private static final Logger log = LoggerFactory.getLogger(AutoTradingService.class);

    private final MarketClient marketClient;
    private final OffworldProperties props;

    private final Set<String> watchedGoods = ConcurrentHashMap.newKeySet();

    private final PlayerClient playerClient;
    private final StationClient stationClient;

    public AutoTradingService(
            MarketClient marketClient,
            OffworldProperties props,
            PlayerClient playerClient,
            StationClient stationClient
    ) {
        this.marketClient = marketClient;
        this.props = props;
        this.playerClient = playerClient;
        this.stationClient = stationClient;
    }

    @PostConstruct
    public void start() {
        if (!props.getTrading().isEnabled()) {
            log.info("Auto trading désactivé");
            return;
        }

        marketClient.streamTrades()
                .map(TradeEvent::goodName)
                .filter(good -> good != null && !good.isBlank())
                .filter(watchedGoods::add)
                .flatMap(this::evaluateGood)
                .doOnError(error -> log.error("Erreur auto trading", error))
                .subscribe();
    }

    private Mono<Void> evaluateGood(String goodName) {
        return Mono.delay(Duration.ofMillis(300))
                .then(marketClient.getOrderBook(goodName))
                .flatMap(book -> decideAndPlaceOrder(goodName, book))
                .doFinally(signal -> watchedGoods.remove(goodName))
                .then();
    }

    private Mono<Void> decideAndPlaceOrder(String goodName, OrderBookDto book) {
        OrderBookLevel bestBid = book.bids() != null && !book.bids().isEmpty() ? book.bids().get(0) : null;
        OrderBookLevel bestAsk = book.asks() != null && !book.asks().isEmpty() ? book.asks().get(0) : null;

        if (bestBid == null || bestAsk == null) {
            return Mono.empty();
        }

        int spread = bestAsk.price() - bestBid.price();
        int minSpread = props.getTrading().getMinSpread();
        int quantity = props.getTrading().getDefaultQuantity();
        String stationPlanetId = props.getTrading().getStationPlanetId();

        return Mono.zip(
                marketClient.getPrices(),
                playerClient.getPlayer(props.getPlayerId()),
                stationClient.getStation(stationPlanetId)
        ).flatMap(tuple -> {

            var prices = tuple.getT1();
            var player = tuple.getT2();
            var station = tuple.getT3();

            int credits = player.credits();
            int stock = station.storage().getOrDefault(goodName, 0);

            log.info("CHECK {} -> credits={}, stock={}", goodName, credits, stock);

            // BUY si spread intéressant et assez de crédits
            if (spread >= minSpread) {
                int buyPrice = bestBid.price() + 1;
                int cost = buyPrice * quantity;

                if (credits >= cost) {
                    return marketClient.createLimitBuyOrder(goodName, buyPrice, quantity, stationPlanetId)
                            .doOnSuccess(order -> log.info("BUY {} @ {}", goodName, buyPrice))
                            .then();
                } else {
                    log.info("Pas assez de crédits pour BUY {}", goodName);
                }
            }

            // SELL si on a du stock
            if (stock >= quantity) {
                int sellPrice = bestBid.price();

                return marketClient.createLimitSellOrder(goodName, sellPrice, quantity, stationPlanetId)
                        .doOnSuccess(order -> log.info("SELL {} @ {}", goodName, sellPrice))
                        .then();
            }

            return Mono.empty();
        });
    }
}

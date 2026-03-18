package com.example.offworld.service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
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

    public AutoTradingService(MarketClient marketClient, OffworldProperties props) {
        this.marketClient = marketClient;
        this.props = props;
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
            log.info("Book incomplet pour {}", goodName);
            return Mono.empty();
        }

        int spread = bestAsk.price() - bestBid.price();
        int minSpread = props.getTrading().getMinSpread();
        int quantity = props.getTrading().getDefaultQuantity();
        String stationPlanetId = props.getTrading().getStationPlanetId();

        log.info("Analyse {} -> bid={}, ask={}, spread={}", goodName, bestBid.price(), bestAsk.price(), spread);

        if (spread >= minSpread) {
            int buyPrice = bestBid.price() + 1;
            return marketClient.createLimitBuyOrder(goodName, buyPrice, quantity, stationPlanetId)
                    .doOnSuccess(order -> log.info(
                    "Ordre BUY placé {} @ {} x{} status={}",
                    order.goodName(), order.price(), order.quantity(), order.status()
            ))
                    .then();
        }

        Integer lastTrade = book.lastTradePrice();
        if (lastTrade != null && bestBid.price() >= lastTrade + minSpread) {
            int sellPrice = bestBid.price();
            return marketClient.createLimitSellOrder(goodName, sellPrice, quantity, stationPlanetId)
                    .doOnSuccess(order -> log.info(
                    "Ordre SELL placé {} @ {} x{} status={}",
                    order.goodName(), order.price(), order.quantity(), order.status()
            ))
                    .then();
        }

        return Mono.empty();
    }
}

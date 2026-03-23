package com.example.offworld.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.TradeEvent;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
public class MarketStreamService {

    private static final Logger log = LoggerFactory.getLogger(MarketStreamService.class);
    private static final Duration SSE_IDLE_TIMEOUT = Duration.ofSeconds(45);

    private final MarketClient marketClient;
    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final SimulationStateService simulationStateService;
    private final Flux<TradeEvent> sharedTradeStream;

    public MarketStreamService(
            MarketClient marketClient,
            OffworldProperties props,
            DebugStateService debugStateService,
            SimulationStateService simulationStateService
    ) {
        this.marketClient = marketClient;
        this.props = props;
        this.debugStateService = debugStateService;
        this.simulationStateService = simulationStateService;
        this.sharedTradeStream = marketClient.streamTrades()
                .timeout(SSE_IDLE_TIMEOUT)
                .onBackpressureBuffer(
                        500,
                        dropped -> log.warn("Trade abandonné faute de capacité: id={}", dropped.id())
                )
                .doOnSubscribe(subscription -> log.info("Connexion au flux SSE marché"))
                .doOnNext(this::recordIncomingTrade)
                .doOnError(error -> log.warn("Flux SSE interrompu: {}", error.getMessage()))
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(30))
                )
                .share();
    }

    @PostConstruct
    public void start() {
        if (props.getMarket().isEnabled() == false) {
            log.info("Market stream désactivé");
            return;
        }

        sharedTradeStream
                .doOnNext(this::handleTrade)
                .doOnError(error -> log.error("Erreur flux SSE côté observabilité", error))
                .subscribe();
    }

    public Flux<TradeEvent> tradeStream() {
        return sharedTradeStream;
    }

    private void handleTrade(TradeEvent trade) {
        log.info("TRADE {} @ {} x{}",
                trade.goodName(),
                trade.price(),
                trade.quantity());
    }

    private void recordIncomingTrade(TradeEvent trade) {
        simulationStateService.recordTrade(trade, "market-stream");
        debugStateService.recordTradeAction(
                "trade:%s price=%d qty=%d".formatted(
                        trade.goodName(),
                        trade.price(),
                        trade.quantity()
                )
        );
    }
}

package com.example.offworld.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.TradeEvent;

import jakarta.annotation.PostConstruct;
import reactor.util.retry.Retry;

@Service
public class MarketStreamService {

    private static final Logger log = LoggerFactory.getLogger(MarketStreamService.class);

    private final MarketClient marketClient;
    private final OffworldProperties props;

    public MarketStreamService(MarketClient marketClient, OffworldProperties props) {
        this.marketClient = marketClient;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        if (!props.getMarket().isEnabled()) {
            log.info("Market stream désactivé");
            return;
        }

        marketClient.streamTrades()
                // évite de saturer si trop d’événements
                .onBackpressureBuffer(500)
                // traitement des events
                .doOnNext(this::handleTrade)
                // retry automatique si coupure SSE
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(30))
                )
                .doOnError(err -> log.error("Erreur flux SSE", err))
                .subscribe();
    }

    private void handleTrade(TradeEvent trade) {
        log.info("TRADE {} @ {} x{}",
                trade.goodName(),
                trade.price(),
                trade.quantity());
    }
}

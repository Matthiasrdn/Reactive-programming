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
    private static final Duration ORDER_BOOK_TIMEOUT = Duration.ofSeconds(5);

    private final MarketClient marketClient;
    private final MarketStreamService marketStreamService;
    private final OffworldProperties props;
    private final PlayerClient playerClient;
    private final StationClient stationClient;
    private final OrderManagementService orderManagementService;
    private final DebugStateService debugStateService;
    private final SimulationStateService simulationStateService;

    private final Set<String> watchedGoods = ConcurrentHashMap.newKeySet();

    public AutoTradingService(
            MarketClient marketClient,
            MarketStreamService marketStreamService,
            OffworldProperties props,
            PlayerClient playerClient,
            StationClient stationClient,
            OrderManagementService orderManagementService,
            DebugStateService debugStateService,
            SimulationStateService simulationStateService
    ) {
        this.marketClient = marketClient;
        this.marketStreamService = marketStreamService;
        this.props = props;
        this.playerClient = playerClient;
        this.stationClient = stationClient;
        this.orderManagementService = orderManagementService;
        this.debugStateService = debugStateService;
        this.simulationStateService = simulationStateService;
    }

    @PostConstruct
    public void start() {
        if (props.getTrading().isEnabled() == false) {
            log.info("Auto trading désactivé");
            return;
        }

        log.info("Auto trading démarré");
        log.info("Configuration trading: stationPlanetId={}, defaultQuantity={}, minSpread={}",
                props.getTrading().getStationPlanetId(),
                props.getTrading().getDefaultQuantity(),
                props.getTrading().getMinSpread()
        );

        marketStreamService.tradeStream()
                .doOnSubscribe(subscription -> log.info("Souscription au flux SSE des trades"))
                .doOnNext(trade -> log.info("TRADE reçu: good={} price={} quantity={} buyer={} seller={}",
                        trade.goodName(),
                        trade.price(),
                        trade.quantity(),
                        trade.buyerId(),
                        trade.sellerId()
                ))
                .map(TradeEvent::goodName)
                .filter(good -> {
                    boolean valid = good != null && good.isBlank() == false;
                    if (valid == false) {
                        log.debug("Trade ignoré: goodName vide");
                    }
                    return valid;
                })
                .filter(good -> {
                    boolean accepted = watchedGoods.add(good);
                    if (accepted == false) {
                        log.debug("Analyse déjà en cours pour {}, trade ignoré temporairement", good);
                    }
                    return accepted;
                })
                .flatMap(this::evaluateGood)
                .doOnError(error -> log.error("Erreur auto trading", error))
                .subscribe(
                        unused -> {
                        },
                        error -> log.error("Flux auto trading terminé en erreur", error),
                        () -> log.warn("Flux auto trading terminé")
                );
    }

    private Mono<Void> evaluateGood(String goodName) {
        log.info("Analyse démarrée pour {}", goodName);
        debugStateService.recordTradeAction("analyse-start:" + goodName);

        return Mono.delay(Duration.ofMillis(300))
                .then(marketClient.getOrderBook(goodName))
                .timeout(ORDER_BOOK_TIMEOUT)
                .doOnSuccess(book -> {
                    if (book != null) {
                        simulationStateService.updateOrderBook(book, "auto-trading:order-book");
                        int bidCount = book.bids() != null ? book.bids().size() : 0;
                        int askCount = book.asks() != null ? book.asks().size() : 0;
                        log.info("Order book récupéré pour {}: bids={}, asks={}", goodName, bidCount, askCount);
                    }
                })
                .flatMap(book -> decideAndPlaceOrder(goodName, book))
                .doOnError(error -> log.error("Erreur pendant l'analyse de {}", goodName, error))
                .doFinally(signal -> {
                    watchedGoods.remove(goodName);
                    log.debug("Analyse libérée pour {} avec signal {}", goodName, signal);
                })
                .onErrorResume(error -> {
                    debugStateService.recordTradeAction("analyse-error:" + goodName);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> decideAndPlaceOrder(String goodName, OrderBookDto book) {
        OrderBookLevel bestBid = book.bids() == null || book.bids().isEmpty() ? null : book.bids().get(0);
        OrderBookLevel bestAsk = book.asks() == null || book.asks().isEmpty() ? null : book.asks().get(0);

        if (bestBid == null || bestAsk == null) {
            log.info("Pas assez de données dans l'order book pour {} (bestBid={}, bestAsk={})",
                    goodName, bestBid, bestAsk);
            return Mono.empty();
        }

        int spread = bestAsk.price() - bestBid.price();
        int minSpread = props.getTrading().getMinSpread();
        int quantity = props.getTrading().getDefaultQuantity();
        String stationPlanetId = props.getTrading().getStationPlanetId();
        String systemName = props.getLogistics().getOriginSystemName();

        log.info("Décision trading pour {}: bestBid={}, bestAsk={}, spread={}, minSpread={}, quantity={}",
                goodName, bestBid.price(), bestAsk.price(), spread, minSpread, quantity);

        return Mono.zip(
                playerClient.getPlayer(props.getPlayerId())
                        .doOnSuccess(player -> log.info("Player récupéré: id={} credits={}",
                                player.id(), player.credits())),
                stationClient.getStation(systemName, stationPlanetId)
                        .doOnSuccess(station -> {
                            simulationStateService.updatePlanetInventory(
                                    systemName,
                                    stationPlanetId,
                                    station,
                                    "auto-trading:station-read"
                            );
                            log.info("Station récupérée: system={} planet={} inventorySize={}",
                                    systemName,
                                    stationPlanetId,
                                    station.inventory() == null ? 0 : station.inventory().size()
                            );
                        })
        ).flatMap(tuple -> {
            var player = tuple.getT1();
            var station = tuple.getT2();

            int credits = player.credits();
            int stock = station.inventory() == null
                    ? 0
                    : station.inventory().getOrDefault(goodName, 0);

            log.info(
                    "CHECK {} -> credits={}, stock={}, bid={}, ask={}, spread={}",
                    goodName, credits, stock, bestBid.price(), bestAsk.price(), spread
            );

            if (spread >= minSpread) {
                int buyPrice = bestBid.price() + 1;
                int cost = buyPrice * quantity;

                log.info("Option BUY détectée pour {}: buyPrice={}, quantity={}, cost={}",
                        goodName, buyPrice, quantity, cost);

                if (credits >= cost) {
                    debugStateService.recordTradeAction("decision:buy " + goodName);
                    return orderManagementService
                            .placeAndMonitorBuyOrder(goodName, buyPrice, quantity, stationPlanetId)
                            .doOnSubscribe(subscription -> log.info("Tentative de BUY pour {}", goodName))
                            .doOnSuccess(order -> {
                                if (order != null) {
                                    debugStateService.recordTradeAction(
                                            "buy-result:%s status=%s".formatted(goodName, order.status())
                                    );
                                    log.info(
                                            "BUY terminé good={} id={} status={}",
                                            goodName, order.id(), order.status()
                                    );
                                } else {
                                    log.info("BUY non lancé pour {} (ordre déjà ouvert ou ignoré)", goodName);
                                }
                            })
                            .doOnError(error -> log.error("Erreur pendant le BUY de {}", goodName, error))
                            .then();
                }

                log.info(
                        "BUY impossible pour {}: crédits insuffisants (credits={}, cost={})",
                        goodName, credits, cost
                );
                debugStateService.recordTradeAction("decision:buy-skipped-credits " + goodName);
            } else {
                log.info("Pas de BUY pour {}: spread trop faible (spread={}, minSpread={})",
                        goodName, spread, minSpread);
            }

            if (stock >= quantity) {
                int sellPrice = bestBid.price();

                log.info("Option SELL détectée pour {}: sellPrice={}, quantity={}, stock={}",
                        goodName, sellPrice, quantity, stock);

                debugStateService.recordTradeAction("decision:sell " + goodName);
                return orderManagementService
                        .placeAndMonitorSellOrder(goodName, sellPrice, quantity, stationPlanetId)
                        .doOnSubscribe(subscription -> log.info("Tentative de SELL pour {}", goodName))
                        .doOnSuccess(order -> {
                            if (order != null) {
                                debugStateService.recordTradeAction(
                                        "sell-result:%s status=%s".formatted(goodName, order.status())
                                );
                                log.info(
                                        "SELL terminé good={} id={} status={}",
                                        goodName, order.id(), order.status()
                                );
                            } else {
                                log.info("SELL non lancé pour {} (ordre déjà ouvert ou ignoré)", goodName);
                            }
                        })
                        .doOnError(error -> log.error("Erreur pendant le SELL de {}", goodName, error))
                        .then();
            }

            log.info(
                    "Aucune action prise pour {}: spread={}, stock={}, quantity={}",
                    goodName, spread, stock, quantity
            );
            debugStateService.recordTradeAction("decision:none " + goodName);

            return Mono.empty();
        });
    }
}

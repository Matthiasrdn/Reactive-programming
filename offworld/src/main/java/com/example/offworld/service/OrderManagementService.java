package com.example.offworld.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.MarketClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class OrderManagementService {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    private static final Duration ORDER_CREATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ORDER_STATUS_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_MONITOR_POLLS = 24;

    private final MarketClient marketClient;
    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final SimulationStateService simulationStateService;

    public OrderManagementService(
            MarketClient marketClient,
            OffworldProperties props,
            DebugStateService debugStateService,
            SimulationStateService simulationStateService
    ) {
        this.marketClient = marketClient;
        this.props = props;
        this.debugStateService = debugStateService;
        this.simulationStateService = simulationStateService;
    }

    @PostConstruct
    public void start() {
        if (props.getOrderManagement().isEnabled() == false) {
            log.info("Order management désactivé");
            return;
        }

        Flux.interval(Duration.ofSeconds(30))
                .flatMap(tick -> manageOrders())
                .onErrorContinue((error, value)
                        -> log.warn("Erreur order management: {}", error.getMessage()))
                .subscribe();
    }

    public Mono<MarketOrderDto> placeManagedBuyOrder(
            String goodName,
            int price,
            int quantity,
            String stationPlanetId
    ) {
        return hasOpenOrder(goodName, "buy")
                .flatMap(hasOrder -> {
                    if (hasOrder) {
                        log.info("BUY ignoré: ordre déjà ouvert pour {}", goodName);
                        debugStateService.recordTradeAction("buy-skipped-open:" + goodName);
                        return Mono.empty();
                    }

                    return marketClient.createLimitBuyOrder(goodName, price, quantity, stationPlanetId)
                            .timeout(ORDER_CREATION_TIMEOUT)
                            .retryWhen(Retry.backoff(2, Duration.ofMillis(300)))
                            .doOnSuccess(order -> {
                                if (order != null) {
                                    simulationStateService.updateOrder(order, "order-management:create-buy");
                                    debugStateService.recordTradeAction("buy-created:" + order.id());
                                    log.info(
                                            "BUY créé id={} good={} price={} qty={}",
                                            order.id(), order.goodName(), order.price(), order.quantity()
                                    );
                                }
                            });
                });
    }

    public Mono<MarketOrderDto> placeManagedSellOrder(
            String goodName,
            int price,
            int quantity,
            String stationPlanetId
    ) {
        return hasOpenOrder(goodName, "sell")
                .flatMap(hasOrder -> {
                    if (hasOrder) {
                        log.info("SELL ignoré: ordre déjà ouvert pour {}", goodName);
                        debugStateService.recordTradeAction("sell-skipped-open:" + goodName);
                        return Mono.empty();
                    }

                    return marketClient.createLimitSellOrder(goodName, price, quantity, stationPlanetId)
                            .timeout(ORDER_CREATION_TIMEOUT)
                            .retryWhen(Retry.backoff(2, Duration.ofMillis(300)))
                            .doOnSuccess(order -> {
                                if (order != null) {
                                    simulationStateService.updateOrder(order, "order-management:create-sell");
                                    debugStateService.recordTradeAction("sell-created:" + order.id());
                                    log.info(
                                            "SELL créé id={} good={} price={} qty={}",
                                            order.id(), order.goodName(), order.price(), order.quantity()
                                    );
                                }
                            });
                });
    }

    public Mono<MarketOrderDto> placeAndMonitorBuyOrder(
            String goodName,
            int price,
            int quantity,
            String stationPlanetId
    ) {
        return placeManagedBuyOrder(goodName, price, quantity, stationPlanetId)
                .flatMap(this::monitorOrderUntilClosed);
    }

    public Mono<MarketOrderDto> placeAndMonitorSellOrder(
            String goodName,
            int price,
            int quantity,
            String stationPlanetId
    ) {
        return placeManagedSellOrder(goodName, price, quantity, stationPlanetId)
                .flatMap(this::monitorOrderUntilClosed);
    }

    public Mono<MarketOrderDto> monitorOrderUntilClosed(MarketOrderDto createdOrder) {
        return Flux.interval(POLL_INTERVAL)
                .startWith(0L)
                .take(MAX_MONITOR_POLLS)
                .concatMap(tick -> findMyOrderById(createdOrder.id()))
                .switchIfEmpty(Mono.just(createdOrder))
                .flatMap(order -> {
                    simulationStateService.updateOrder(order, "order-management:monitor");

                    if (isTerminalOrder(order)) {
                        debugStateService.recordTradeAction(
                                "order-terminal:%s status=%s".formatted(order.id(), order.status())
                        );
                        log.info("Ordre terminal id={} status={}", order.id(), order.status());
                        return Mono.just(order);
                    }

                    if (isTooOld(order)) {
                        debugStateService.recordTradeAction("order-stale:" + order.id());
                        return cancelOrder(order)
                                .then(findMyOrderById(order.id()).defaultIfEmpty(order));
                    }

                    log.debug("Ordre encore actif id={} status={}", order.id(), order.status());
                    return Mono.just(order);
                })
                .filter(this::isTerminalOrder)
                .next()
                .switchIfEmpty(
                        cancelOrder(createdOrder)
                                .then(findMyOrderById(createdOrder.id()).defaultIfEmpty(createdOrder))
                )
                .timeout(Duration.ofSeconds(props.getOrderManagement().getCancelAfterSeconds() + 10L))
                .doOnSuccess(order -> {
                    if (order != null) {
                        simulationStateService.updateOrder(order, "order-management:finished");
                        debugStateService.recordTradeAction(
                                "order-finished:%s status=%s".formatted(order.id(), order.status())
                        );
                        log.info(
                                "Fin suivi ordre id={} good={} status={} filled={}/{}",
                                order.id(), order.goodName(), order.status(),
                                order.filledQuantity(), order.quantity()
                        );
                    }
                });
    }

    public Mono<Void> cancelOrderIfStale(MarketOrderDto order) {
        if (isTooOld(order) == false) {
            return Mono.empty();
        }
        return cancelOrder(order);
    }

    public Mono<Boolean> hasOpenOrder(String goodName, String side) {
        return getMyOrdersSafely()
                .filter(order -> goodName.equals(order.goodName()))
                .filter(order -> side.equalsIgnoreCase(order.side()))
                .filter(this::isOpenOrder)
                .hasElements();
    }

    public Mono<MarketOrderDto> findMyOrderById(String orderId) {
        return getMyOrdersSafely()
                .filter(order -> orderId.equals(order.id()))
                .next()
                .onErrorResume(error -> {
                    log.warn("Impossible de récupérer l'ordre {}: {}", orderId, error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> cancelOrder(MarketOrderDto order) {
        return marketClient.cancelOrder(order.id())
                .timeout(ORDER_STATUS_TIMEOUT)
                .doOnSuccess(v -> log.info(
                        "Ordre annulé id={} good={} side={}",
                        order.id(), order.goodName(), order.side()
                ))
                .doOnSuccess(v -> simulationStateService.removeOrder(order.id(), "order-management:cancel"))
                .doOnSuccess(v -> debugStateService.recordTradeAction("order-cancelled:" + order.id()))
                .onErrorResume(error -> {
                    log.warn("Annulation impossible pour {}: {}", order.id(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> manageOrders() {
        return getMyOrdersSafely()
                .collectList()
                .flatMap(this::processOrders);
    }

    private Mono<Void> processOrders(List<MarketOrderDto> orders) {
        orders.forEach(order -> simulationStateService.updateOrder(order, "order-management:sync"));

        List<MarketOrderDto> openOrders = orders.stream()
                .filter(this::isOpenOrder)
                .toList();

        Map<String, List<MarketOrderDto>> byGood = openOrders.stream()
                .collect(Collectors.groupingBy(MarketOrderDto::goodName));

        Mono<Void> cancelTooManyOrders = Flux.fromIterable(byGood.entrySet())
                .flatMap(entry -> cancelExcessOrders(entry.getKey(), entry.getValue()))
                .then();

        Mono<Void> cancelOldOrders = Flux.fromIterable(openOrders)
                .filter(this::isTooOld)
                .flatMap(this::cancelOrderIfStale)
                .then();

        return cancelTooManyOrders.then(cancelOldOrders);
    }

    private Mono<Void> cancelExcessOrders(String goodName, List<MarketOrderDto> orders) {
        int max = props.getOrderManagement().getMaxOpenOrdersPerGood();

        if (orders.size() <= max) {
            return Mono.empty();
        }

        List<MarketOrderDto> sorted = orders.stream()
                .sorted(Comparator.comparingLong(MarketOrderDto::createdAt))
                .toList();

        List<MarketOrderDto> toCancel = sorted.subList(0, sorted.size() - max);

        return Flux.fromIterable(toCancel)
                .flatMap(order -> cancelOrder(order)
                        .doOnSuccess(v -> log.info(
                                "Ordre annulé car trop d'ordres ouverts: id={} good={} side={}",
                                order.id(), order.goodName(), order.side()
                        )))
                .then();
    }

    private boolean isOpenOrder(MarketOrderDto order) {
        if (order.status() == null) {
            return false;
        }

        return order.status().equalsIgnoreCase("open")
                || order.status().equalsIgnoreCase("partial")
                || order.status().equalsIgnoreCase("partially_filled");
    }

    private boolean isTerminalOrder(MarketOrderDto order) {
        if (order.status() == null) {
            return false;
        }

        return order.status().equalsIgnoreCase("filled")
                || order.status().equalsIgnoreCase("cancelled")
                || order.status().equalsIgnoreCase("rejected");
    }

    private boolean isTooOld(MarketOrderDto order) {
        long now = Instant.now().getEpochSecond();
        long ageSeconds = now - order.createdAt();
        return ageSeconds >= props.getOrderManagement().getCancelAfterSeconds();
    }

    private Flux<MarketOrderDto> getMyOrdersSafely() {
        return marketClient.getMyOrders()
                .timeout(ORDER_STATUS_TIMEOUT)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(250)))
                .onErrorResume(error -> {
                    log.warn("Lecture des ordres impossible: {}", error.getMessage());
                    return Flux.empty();
                });
    }
}

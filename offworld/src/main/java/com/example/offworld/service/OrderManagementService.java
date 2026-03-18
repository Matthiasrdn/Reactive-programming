package com.example.offworld.service;

import java.time.Duration;
import java.time.Instant;
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

@Service
public class OrderManagementService {

    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    private final MarketClient marketClient;
    private final OffworldProperties props;

    public OrderManagementService(MarketClient marketClient, OffworldProperties props) {
        this.marketClient = marketClient;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        if (!props.getOrderManagement().isEnabled()) {
            log.info("Order management désactivé");
            return;
        }

        Flux.interval(Duration.ofSeconds(30))
                .flatMap(tick -> manageOrders())
                .onErrorContinue((error, value)
                        -> log.warn("Erreur order management: {}", error.getMessage()))
                .subscribe();
    }

    private Mono<Void> manageOrders() {
        return marketClient.getMyOrders()
                .collectList()
                .flatMap(this::processOrders);
    }

    private Mono<Void> processOrders(List<MarketOrderDto> orders) {
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
                .flatMap(order -> marketClient.cancelOrder(order.id())
                .doOnSuccess(v -> log.info("Ordre annulé car trop vieux: {} {}", order.id(), order.goodName())))
                .then();

        return cancelTooManyOrders.then(cancelOldOrders);
    }

    private Mono<Void> cancelExcessOrders(String goodName, List<MarketOrderDto> orders) {
        int max = props.getOrderManagement().getMaxOpenOrdersPerGood();

        if (orders.size() <= max) {
            return Mono.empty();
        }

        List<MarketOrderDto> sorted = orders.stream()
                .sorted((a, b) -> Long.compare(a.createdAt(), b.createdAt()))
                .toList();

        List<MarketOrderDto> toCancel = sorted.subList(0, sorted.size() - max);

        return Flux.fromIterable(toCancel)
                .flatMap(order -> marketClient.cancelOrder(order.id())
                .doOnSuccess(v -> log.info(
                "Ordre annulé car trop d'ordres ouverts: {} {} {}",
                order.id(), order.goodName(), order.side()
        )))
                .then();
    }

    private boolean isOpenOrder(MarketOrderDto order) {
        return order.status() != null
                && (order.status().equalsIgnoreCase("open")
                || order.status().equalsIgnoreCase("partial")
                || order.status().equalsIgnoreCase("partially_filled"));
    }

    private boolean isTooOld(MarketOrderDto order) {
        long now = Instant.now().getEpochSecond();
        long ageSeconds = now - order.createdAt();
        return ageSeconds >= props.getOrderManagement().getCancelAfterSeconds();
    }
}

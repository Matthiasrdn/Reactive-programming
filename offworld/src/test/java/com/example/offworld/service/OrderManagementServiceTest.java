package com.example.offworld.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.offworld.api.MarketClient;
import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OrderManagementServiceTest {

    @Mock
    private MarketClient marketClient;

    @Mock
    private DebugStateService debugStateService;

    @Mock
    private SimulationStateService simulationStateService;

    private OrderManagementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        OffworldProperties props = new OffworldProperties();
        props.getOrderManagement().setCancelAfterSeconds(120);

        service = new OrderManagementService(
                marketClient,
                props,
                debugStateService,
                simulationStateService
        );
    }

    @Test
    void hasOpenOrderReturnsTrueWhenMatchingOpenOrderExists() {
        MarketOrderDto openBuyOrder = new MarketOrderDto(
                "order-1",
                "beta-corp",
                "iron_ore",
                "buy",
                "limit",
                42,
                10,
                0,
                "open",
                "Proxima Centauri-1",
                Instant.now().getEpochSecond()
        );

        when(marketClient.getMyOrders()).thenReturn(Flux.just(openBuyOrder));

        StepVerifier.create(service.hasOpenOrder("iron_ore", "buy"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void cancelOrderIfStaleCancelsOldOpenOrder() {
        MarketOrderDto staleOrder = new MarketOrderDto(
                "order-2",
                "beta-corp",
                "rare_metals",
                "sell",
                "limit",
                95,
                5,
                0,
                "open",
                "Proxima Centauri-1",
                Instant.now().minusSeconds(180).getEpochSecond()
        );

        when(marketClient.cancelOrder("order-2")).thenReturn(Mono.empty());

        StepVerifier.create(service.cancelOrderIfStale(staleOrder))
                .verifyComplete();

        verify(marketClient).cancelOrder("order-2");
    }

    @Test
    void cancelOrderIfStaleIgnoresFreshOrder() {
        MarketOrderDto freshOrder = new MarketOrderDto(
                "order-3",
                "beta-corp",
                "water",
                "buy",
                "limit",
                12,
                20,
                0,
                "open",
                "Proxima Centauri-1",
                Instant.now().getEpochSecond()
        );

        StepVerifier.create(service.cancelOrderIfStale(freshOrder))
                .verifyComplete();

        verify(marketClient, never()).cancelOrder("order-3");
    }
}

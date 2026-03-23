package com.example.offworld.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.OrderBookLevel;
import com.example.offworld.dto.market.TradeEvent;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.station.StationDto;

class SimulationStateServiceTest {

    private final SimulationStateService service = new SimulationStateService();

    @Test
    void snapshotContainsPlanetShipOrderAndMarketData() {
        service.updatePlanetInventory("Sol", "Sol-3", new StationDto(
                "Terra Station",
                "player-1",
                Map.of("water", 120, "iron_ore", 45)
        ));

        service.updateOrder(new MarketOrderDto(
                "order-1",
                "player-1",
                "water",
                "buy",
                "limit",
                14,
                10,
                2,
                "open",
                "Sol-3",
                1_710_000_000L
        ));

        service.updateShip(new ShipDto(
                "ship-1",
                "player-1",
                "Sol-3",
                "Proxima Centauri-1",
                Map.of("water", 10),
                "in_transit",
                "truck-1",
                250,
                1_710_000_100L,
                1_710_000_200L,
                null
        ));

        service.updateOrderBook(new OrderBookDto(
                "water",
                List.of(new OrderBookLevel(12, 20, 1)),
                List.of(new OrderBookLevel(16, 15, 1)),
                15
        ));

        service.recordTrade(new TradeEvent(
                "trade-1",
                "water",
                15,
                4,
                "buyer-1",
                "seller-1",
                "Sol-3",
                "Mars-1",
                1_710_000_300L
        ));

        SimulationStateService.SimulationSnapshot snapshot = service.snapshot();

        assertThat(snapshot.planets()).containsKey("Sol-3");
        assertThat(snapshot.planets().get("Sol-3").inventory()).containsEntry("water", 120);

        assertThat(snapshot.activeOrders()).containsKey("order-1");
        assertThat(snapshot.activeOrders().get("order-1").status()).isEqualTo("open");

        assertThat(snapshot.ships()).containsKey("ship-1");
        assertThat(snapshot.ships().get("ship-1").status()).isEqualTo("in_transit");

        assertThat(snapshot.marketPrices()).containsKey("water");
        assertThat(snapshot.marketPrices().get("water").bestBid()).isEqualTo(12);
        assertThat(snapshot.marketPrices().get("water").bestAsk()).isEqualTo(16);
        assertThat(snapshot.marketPrices().get("water").spread()).isEqualTo(4);
        assertThat(snapshot.marketPrices().get("water").lastTradePrice()).isEqualTo(15);
        assertThat(snapshot.marketPrices().get("water").lastTradeQuantity()).isEqualTo(4);
    }

    @Test
    void terminalOrderIsRemovedFromActiveOrders() {
        service.updateOrder(new MarketOrderDto(
                "order-2",
                "player-1",
                "iron_ore",
                "sell",
                "limit",
                44,
                8,
                0,
                "open",
                "Sol-3",
                1_710_000_000L
        ));

        service.updateOrder(new MarketOrderDto(
                "order-2",
                "player-1",
                "iron_ore",
                "sell",
                "limit",
                44,
                8,
                8,
                "filled",
                "Sol-3",
                1_710_000_000L
        ));

        assertThat(service.snapshot().activeOrders()).doesNotContainKey("order-2");
    }
}

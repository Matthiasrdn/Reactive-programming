package com.example.offworld.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.OrderBookLevel;
import com.example.offworld.dto.market.TradeEvent;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.station.StationDto;
import com.example.offworld.dto.player.PlayerDto;

class SimulationStateServiceTest {

    private SimulationStateService service;

    @BeforeEach
    void setUp() {
        OffworldProperties props = new OffworldProperties();
        props.setBaseUrl("http://localhost:3000");
        props.setPlayerId("player-1");

        OffworldProperties.Trading trading = new OffworldProperties.Trading();
        trading.setStationPlanetId("Sol-3");
        props.setTrading(trading);

        OffworldProperties.Logistics logistics = new OffworldProperties.Logistics();
        logistics.setEnabled(true);
        logistics.setOriginSystemName("Sol");
        logistics.setOriginPlanetId("Sol-3");
        logistics.setDestinationPlanetId("Proxima Centauri-1");
        logistics.setGoodName("water");
        logistics.setQuantity(10);
        props.setLogistics(logistics);

        service = new SimulationStateService(props);
    }

    @Test
    void snapshotContainsPlanetShipOrderAndMarketData() {
        service.updatePlanetInventory("Sol", "Sol-3", new StationDto(
                "Terra Station",
                "player-1",
                Map.of("water", 120, "iron_ore", 45)
        ), "test");

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
        ), "test");

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
        ), "test");

        service.updateOrderBook(new OrderBookDto(
                "water",
                List.of(new OrderBookLevel(12, 20, 1)),
                List.of(new OrderBookLevel(16, 15, 1)),
                15
        ), "test");

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
        ), "test");

        Map<String, Object> snapshot = service.snapshot();

        @SuppressWarnings("unchecked")
        Map<String, SimulationStateService.PlanetState> planets
                = (Map<String, SimulationStateService.PlanetState>) snapshot.get("planets");

        @SuppressWarnings("unchecked")
        Map<String, SimulationStateService.OrderState> activeOrders
                = (Map<String, SimulationStateService.OrderState>) snapshot.get("activeOrders");

        @SuppressWarnings("unchecked")
        Map<String, SimulationStateService.ShipState> ships
                = (Map<String, SimulationStateService.ShipState>) snapshot.get("ships");

        @SuppressWarnings("unchecked")
        Map<String, SimulationStateService.MarketPriceState> marketPrices
                = (Map<String, SimulationStateService.MarketPriceState>) snapshot.get("marketPrices");

        @SuppressWarnings("unchecked")
        List<SimulationStateService.TradeState> recentTrades
                = (List<SimulationStateService.TradeState>) snapshot.get("recentTrades");

        assertThat(planets).containsKey("Sol-3");
        assertThat(planets.get("Sol-3").inventory()).containsEntry("water", 120);

        assertThat(activeOrders).containsKey("order-1");
        assertThat(activeOrders.get("order-1").status()).isEqualTo("open");

        assertThat(ships).containsKey("ship-1");
        assertThat(ships.get("ship-1").status()).isEqualTo("in_transit");

        assertThat(marketPrices).containsKey("water");
        assertThat(marketPrices.get("water").bestBid()).isEqualTo(12);
        assertThat(marketPrices.get("water").bestAsk()).isEqualTo(16);
        assertThat(marketPrices.get("water").spread()).isEqualTo(4);
        assertThat(marketPrices.get("water").lastPrice()).isEqualTo(15);

        assertThat(recentTrades).hasSize(1);
        assertThat(recentTrades.get(0).goodName()).isEqualTo("water");
        assertThat(recentTrades.get(0).price()).isEqualTo(15);
        assertThat(recentTrades.get(0).quantity()).isEqualTo(4);
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
        ), "test");

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
        ), "test");

        Map<String, Object> snapshot = service.snapshot();

        @SuppressWarnings("unchecked")
        Map<String, SimulationStateService.OrderState> activeOrders
                = (Map<String, SimulationStateService.OrderState>) snapshot.get("activeOrders");

        assertThat(activeOrders).doesNotContainKey("order-2");
    }
}

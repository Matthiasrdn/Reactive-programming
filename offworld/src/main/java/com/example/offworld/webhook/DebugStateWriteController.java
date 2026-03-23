package com.example.offworld.webhook;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.TradeEvent;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.station.StationDto;
import com.example.offworld.service.SimulationStateService;

@RestController
@RequestMapping("/debug/state")
public class DebugStateWriteController {

    private final SimulationStateService simulationStateService;

    public DebugStateWriteController(SimulationStateService simulationStateService) {
        this.simulationStateService = simulationStateService;
    }

    @PostMapping(path = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> reset() {
        simulationStateService.clearAll();
        return Map.of(
                "ok", true,
                "message", "State reset"
        );
    }

    @PostMapping(path = "/planet", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsertPlanet(
            @RequestParam String systemName,
            @RequestParam(defaultValue = "curl") String source,
            @RequestBody PlanetPayload payload
    ) {
        simulationStateService.updatePlanetInventory(
                systemName,
                payload.planetId(),
                payload.station(),
                source
        );

        return Map.of(
                "ok", true,
                "message", "Planet updated",
                "planetId", payload.planetId()
        );
    }

    @PostMapping(path = "/ship", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsertShip(
            @RequestParam(defaultValue = "curl") String source,
            @RequestBody ShipDto ship
    ) {
        simulationStateService.updateShip(ship, source);
        return Map.of(
                "ok", true,
                "message", "Ship updated",
                "shipId", ship.id()
        );
    }

    @PostMapping(path = "/order", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upsertOrder(
            @RequestParam(defaultValue = "curl") String source,
            @RequestBody MarketOrderDto order
    ) {
        simulationStateService.updateOrder(order, source);
        return Map.of(
                "ok", true,
                "message", "Order updated",
                "orderId", order.id()
        );
    }

    @DeleteMapping(path = "/order/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> removeOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "curl") String source
    ) {
        simulationStateService.removeOrder(orderId, source);
        return Map.of(
                "ok", true,
                "message", "Order removed",
                "orderId", orderId
        );
    }

    @PostMapping(path = "/trade", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> addTrade(
            @RequestParam(defaultValue = "curl") String source,
            @RequestBody TradeEvent trade
    ) {
        simulationStateService.recordTrade(trade, source);
        return Map.of(
                "ok", true,
                "message", "Trade added",
                "tradeId", trade.id()
        );
    }

    @PostMapping(path = "/order-book", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateOrderBook(
            @RequestParam(defaultValue = "curl") String source,
            @RequestBody OrderBookDto orderBook
    ) {
        simulationStateService.updateOrderBook(orderBook, source);
        return Map.of(
                "ok", true,
                "message", "Order book updated",
                "goodName", orderBook.goodName()
        );
    }

    public record PlanetPayload(
            String planetId,
            StationDto station
            ) {

    }
}

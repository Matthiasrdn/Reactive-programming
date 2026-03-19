package com.example.offworld.webhook;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.offworld.config.OffworldProperties;
import com.example.offworld.service.DebugShipService;
import com.example.offworld.service.DebugStateService;
import com.example.offworld.service.LogisticsService;

import reactor.core.publisher.Mono;

@RestController
public class DebugController {

    private final OffworldProperties props;
    private final DebugStateService debugStateService;
    private final DebugShipService debugShipService;
    private final LogisticsService logisticsService;

    public DebugController(
            OffworldProperties props,
            DebugStateService debugStateService,
            DebugShipService debugShipService,
            LogisticsService logisticsService
    ) {
        this.props = props;
        this.debugStateService = debugStateService;
        this.debugShipService = debugShipService;
        this.logisticsService = logisticsService;
    }

    @GetMapping("/debug/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", props.getBaseUrl());
        result.put("playerId", props.getPlayerId());
        result.put("callbackUrl", props.getCallbackUrl());
        result.put("marketEnabled", props.getMarket().isEnabled());
        result.put("tradingEnabled", props.getTrading().isEnabled());
        result.put("logisticsEnabled", props.getLogistics().isEnabled());
        result.put("orderManagementEnabled", props.getOrderManagement().isEnabled());
        result.put("state", debugStateService.snapshot());
        return result;
    }

    @PostMapping("/debug/logistics/launch")
    public Mono<ResponseEntity<String>> launchLogistics() {
        return logisticsService.forceLaunchNow()
                .thenReturn(ResponseEntity.ok("Trucking launch triggered"));
    }

    @GetMapping("/debug/ships")
    public Map<String, Object> allShips() {
        return debugShipService.getAllShips();
    }

    @GetMapping("/debug/ships/{shipId}")
    public Map<String, Object> oneShip(@PathVariable String shipId) {
        return debugShipService.getShip(shipId);
    }
}

package com.example.offworld.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class DebugStateService {

    private final AtomicReference<String> lastHttpRequest = new AtomicReference<>("none");
    private final AtomicReference<String> lastHttpResponse = new AtomicReference<>("none");
    private final AtomicReference<String> lastWebhook = new AtomicReference<>("none");
    private final AtomicReference<String> lastShipAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastLogisticsAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastTradeAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastUpdateTime = new AtomicReference<>(Instant.now().toString());

    public void recordHttpRequest(String value) {
        lastHttpRequest.set(value);
        touch();
    }

    public void recordHttpResponse(String value) {
        lastHttpResponse.set(value);
        touch();
    }

    public void recordWebhook(String value) {
        lastWebhook.set(value);
        touch();
    }

    public void recordShipAction(String value) {
        lastShipAction.set(value);
        touch();
    }

    public void recordLogisticsAction(String value) {
        lastLogisticsAction.set(value);
        touch();
    }

    public void recordTradeAction(String value) {
        lastTradeAction.set(value);
        touch();
    }

    public Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("lastHttpRequest", lastHttpRequest.get());
        result.put("lastHttpResponse", lastHttpResponse.get());
        result.put("lastWebhook", lastWebhook.get());
        result.put("lastShipAction", lastShipAction.get());
        result.put("lastLogisticsAction", lastLogisticsAction.get());
        result.put("lastTradeAction", lastTradeAction.get());
        result.put("lastUpdateTime", lastUpdateTime.get());
        return result;
    }

    private void touch() {
        lastUpdateTime.set(Instant.now().toString());
    }
}

package com.example.offworld.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class DebugStateService {

    private static final int MAX_EVENTS = 40;

    private final AtomicReference<String> lastHttpRequest = new AtomicReference<>("none");
    private final AtomicReference<String> lastHttpResponse = new AtomicReference<>("none");
    private final AtomicReference<String> lastWebhook = new AtomicReference<>("none");
    private final AtomicReference<String> lastShipAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastLogisticsAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastTradeAction = new AtomicReference<>("none");
    private final AtomicReference<String> lastUpdateTime = new AtomicReference<>(Instant.now().toString());
    private final ConcurrentLinkedDeque<Map<String, String>> recentEvents = new ConcurrentLinkedDeque<>();

    public void recordHttpRequest(String value) {
        lastHttpRequest.set(value);
        recordEvent("http-request", value);
    }

    public void recordHttpResponse(String value) {
        lastHttpResponse.set(value);
        recordEvent("http-response", value);
    }

    public void recordWebhook(String value) {
        lastWebhook.set(value);
        recordEvent("webhook", value);
    }

    public void recordShipAction(String value) {
        lastShipAction.set(value);
        recordEvent("ship", value);
    }

    public void recordLogisticsAction(String value) {
        lastLogisticsAction.set(value);
        recordEvent("logistics", value);
    }

    public void recordTradeAction(String value) {
        lastTradeAction.set(value);
        recordEvent("trade", value);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lastHttpRequest", lastHttpRequest.get());
        result.put("lastHttpResponse", lastHttpResponse.get());
        result.put("lastWebhook", lastWebhook.get());
        result.put("lastShipAction", lastShipAction.get());
        result.put("lastLogisticsAction", lastLogisticsAction.get());
        result.put("lastTradeAction", lastTradeAction.get());
        result.put("lastUpdateTime", lastUpdateTime.get());
        result.put("recentEvents", new ArrayList<>(recentEvents));
        return result;
    }

    private void recordEvent(String channel, String value) {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("time", Instant.now().toString());
        event.put("channel", channel);
        event.put("value", value);

        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.pollLast();
        }

        touch();
    }

    private void touch() {
        lastUpdateTime.set(Instant.now().toString());
    }
}

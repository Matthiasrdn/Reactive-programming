package com.example.offworld.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.OrderBookLevel;
import com.example.offworld.dto.market.TradeEvent;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.station.StationDto;

@Service
public class SimulationStateService {

    private static final int MAX_RECENT_EVENTS = 80;
    private static final int MAX_RECENT_TRADES = 120;

    private final Map<String, PlanetState> planets = new ConcurrentHashMap<>();
    private final Map<String, OrderState> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, ShipState> ships = new ConcurrentHashMap<>();
    private final Map<String, MarketPriceState> marketPrices = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<TradeState> recentTrades = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<StateEvent> recentEvents = new ConcurrentLinkedDeque<>();
    private final AtomicReference<String> lastUpdatedAt = new AtomicReference<>(Instant.now().toString());

    public SimulationStateService(OffworldProperties props) {
        registerPlanet(props.getTrading().getStationPlanetId(), "trading-station");
        registerPlanet(props.getLogistics().getOriginPlanetId(), "logistics-origin");
        registerPlanet(props.getLogistics().getDestinationPlanetId(), "logistics-destination");
    }

    public void registerPlanet(String planetId, String source) {
        if (planetId == null || planetId.isBlank()) {
            return;
        }

        planets.compute(planetId, (key, existing) -> {
            PlanetState base = existing != null ? existing : PlanetState.empty(key);
            return new PlanetState(
                    base.planetId(),
                    base.displayName(),
                    base.systemName(),
                    new LinkedHashMap<>(base.inventory()),
                    source,
                    now()
            );
        });

        recordEvent("planet", "Planete %s observee".formatted(planetId));
    }

    public void updatePlanetInventory(String systemName, String planetId, StationDto station, String source) {
        if (planetId == null || planetId.isBlank() || station == null) {
            return;
        }

        Map<String, Integer> inventoryCopy = copyMap(station.inventory());

        planets.put(planetId, new PlanetState(
                planetId,
                station.name() != null && !station.name().isBlank() ? station.name() : planetId,
                systemName,
                inventoryCopy,
                source,
                now()
        ));

        recordEvent("planet", buildPlanetInventoryMessage(planetId, inventoryCopy));
    }

    public void updateOrder(MarketOrderDto order, String source) {
        if (order == null || order.id() == null || order.id().isBlank()) {
            return;
        }

        OrderState orderState = OrderState.from(order, source, now());

        if (isTerminalStatus(order.status())) {
            activeOrders.remove(order.id());
            recordEvent("order", buildOrderMessage(order));
            return;
        }

        activeOrders.put(order.id(), orderState);
        recordEvent("order", buildOrderMessage(order));
    }

    public void removeOrder(String orderId, String source) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }

        activeOrders.remove(orderId);
        recordEvent("order", "Ordre %s retire".formatted(orderId));
    }

    public void updateShip(ShipDto ship, String source) {
        if (ship == null || ship.id() == null || ship.id().isBlank()) {
            return;
        }

        if (ship.originPlanetId() != null) {
            registerPlanet(ship.originPlanetId(), "ship-origin");
        }
        if (ship.destinationPlanetId() != null) {
            registerPlanet(ship.destinationPlanetId(), "ship-destination");
        }

        ships.put(ship.id(), ShipState.from(ship, source, now()));
        recordEvent("ship", buildShipMessage(ship));
    }

    public void updateMarketPrices(Map<String, Integer> prices, String source) {
        if (prices == null || prices.isEmpty()) {
            return;
        }

        String updatedAt = now();
        prices.forEach((goodName, price) -> {
            if (goodName == null || goodName.isBlank()) {
                return;
            }

            marketPrices.put(goodName, new MarketPriceState(goodName, price, null, null, null, source, updatedAt));
        });

        touch(updatedAt);
        recordEvent("market", "Prix marche mis a jour (%d ressources)".formatted(prices.size()));
    }

    public void updateOrderBook(OrderBookDto orderBook, String source) {
        if (orderBook == null || orderBook.goodName() == null || orderBook.goodName().isBlank()) {
            return;
        }

        OrderBookLevel bestBid = firstLevel(orderBook.bids());
        OrderBookLevel bestAsk = firstLevel(orderBook.asks());
        Integer spread = bestBid != null && bestAsk != null
                ? bestAsk.price() - bestBid.price()
                : null;
        String updatedAt = now();

        marketPrices.put(orderBook.goodName(), new MarketPriceState(
                orderBook.goodName(),
                orderBook.lastTradePrice(),
                bestBid != null ? bestBid.price() : null,
                bestAsk != null ? bestAsk.price() : null,
                spread,
                source,
                updatedAt
        ));

        touch(updatedAt);
        recordEvent("market", buildOrderBookMessage(orderBook.goodName(), bestBid, bestAsk));
    }

    public void recordTrade(TradeEvent trade, String source) {
        if (trade == null || trade.id() == null || trade.id().isBlank()) {
            return;
        }

        recentTrades.addFirst(TradeState.from(trade, source));
        trimDeque(recentTrades, MAX_RECENT_TRADES);

        recordEvent(
                "trade",
                "Trade %s @ %d x%d".formatted(
                        safe(trade.goodName()),
                        trade.price(),
                        trade.quantity()
                )
        );
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", lastUpdatedAt.get());
        result.put("planets", new LinkedHashMap<>(planets));
        result.put("activeOrders", new LinkedHashMap<>(activeOrders));
        result.put("ships", new LinkedHashMap<>(ships));
        result.put("marketPrices", new LinkedHashMap<>(marketPrices));
        result.put("recentTrades", new ArrayList<>(recentTrades));
        result.put("recentEvents", new ArrayList<>(recentEvents));
        result.put("counts", Map.of(
                "planets", planets.size(),
                "activeOrders", activeOrders.size(),
                "ships", ships.size(),
                "marketPrices", marketPrices.size(),
                "recentTrades", recentTrades.size()
        ));
        return result;
    }

    public Map<String, PlanetState> planetStates() {
        return new LinkedHashMap<>(planets);
    }

    public Map<String, OrderState> activeOrderStates() {
        return new LinkedHashMap<>(activeOrders);
    }

    public Map<String, ShipState> shipStates() {
        return new LinkedHashMap<>(ships);
    }

    public Map<String, MarketPriceState> marketPriceStates() {
        return new LinkedHashMap<>(marketPrices);
    }

    public List<TradeState> recentTradeStates() {
        return new ArrayList<>(recentTrades);
    }

    private OrderBookLevel firstLevel(List<OrderBookLevel> levels) {
        return levels == null || levels.isEmpty() ? null : levels.get(0);
    }

    private void recordEvent(String type, String message) {
        String timestamp = now();
        recentEvents.addFirst(new StateEvent(type, message, timestamp));
        trimDeque(recentEvents, MAX_RECENT_EVENTS);
        touch(timestamp);
    }

    private void touch(String timestamp) {
        lastUpdatedAt.set(timestamp);
    }

    private String now() {
        return Instant.now().toString();
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }

        return status.equalsIgnoreCase("filled")
                || status.equalsIgnoreCase("cancelled")
                || status.equalsIgnoreCase("rejected");
    }

    private Map<String, Integer> copyMap(Map<String, Integer> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private <T> void trimDeque(ConcurrentLinkedDeque<T> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.pollLast();
        }
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }

    private String buildPlanetInventoryMessage(String planetId, Map<String, Integer> inventory) {
        Map.Entry<String, Integer> firstEntry = inventory.entrySet().stream().findFirst().orElse(null);
        if (firstEntry == null) {
            return "Stock %s observe".formatted(planetId);
        }
        return "Stock %s %s=%d".formatted(planetId, firstEntry.getKey(), firstEntry.getValue());
    }

    private String buildOrderMessage(MarketOrderDto order) {
        return "Ordre %s %s %s".formatted(
                safe(order.side()).toUpperCase(),
                safe(order.goodName()),
                safe(order.status()).toUpperCase()
        );
    }

    private String buildShipMessage(ShipDto ship) {
        return "Ship %s %s -> %s %s".formatted(
                ship.id(),
                safe(ship.originPlanetId()),
                safe(ship.destinationPlanetId()),
                safe(ship.status()).toUpperCase()
        );
    }

    private String buildOrderBookMessage(String goodName, OrderBookLevel bestBid, OrderBookLevel bestAsk) {
        return "Marche %s bid=%s ask=%s".formatted(
                safe(goodName),
                bestBid != null ? bestBid.price() : "n/a",
                bestAsk != null ? bestAsk.price() : "n/a"
        );
    }

    public record PlanetState(
            String planetId,
            String displayName,
            String systemName,
            Map<String, Integer> inventory,
            String source,
            String updatedAt
    ) {
        static PlanetState empty(String planetId) {
            return new PlanetState(
                    planetId,
                    planetId,
                    null,
                    new LinkedHashMap<>(),
                    "bootstrap",
                    Instant.now().toString()
            );
        }
    }

    public record OrderState(
            String orderId,
            String playerId,
            String goodName,
            String side,
            String orderType,
            Integer price,
            int quantity,
            int filledQuantity,
            String status,
            String stationPlanetId,
            long createdAt,
            String source,
            String updatedAt
    ) {
        static OrderState from(MarketOrderDto order, String source, String updatedAt) {
            return new OrderState(
                    order.id(),
                    order.playerId(),
                    order.goodName(),
                    order.side(),
                    order.orderType(),
                    order.price(),
                    order.quantity(),
                    order.filledQuantity(),
                    order.status(),
                    order.stationPlanetId(),
                    order.createdAt(),
                    source,
                    updatedAt
            );
        }
    }

    public record ShipState(
            String shipId,
            String ownerId,
            String originPlanetId,
            String destinationPlanetId,
            Map<String, Integer> cargo,
            String status,
            String truckingId,
            Integer fee,
            Long createdAt,
            Long arrivalAt,
            Long operationCompleteAt,
            String source,
            String updatedAt
    ) {
        static ShipState from(ShipDto ship, String source, String updatedAt) {
            return new ShipState(
                    ship.id(),
                    ship.ownerId(),
                    ship.originPlanetId(),
                    ship.destinationPlanetId(),
                    ship.cargo() == null ? Map.of() : new LinkedHashMap<>(ship.cargo()),
                    ship.status(),
                    ship.truckingId(),
                    ship.fee(),
                    ship.createdAt(),
                    ship.arrivalAt(),
                    ship.operationCompleteAt(),
                    source,
                    updatedAt
            );
        }
    }

    public record MarketPriceState(
            String goodName,
            Integer lastPrice,
            Integer bestBid,
            Integer bestAsk,
            Integer spread,
            String source,
            String updatedAt
    ) {
    }

    public record TradeState(
            String tradeId,
            String goodName,
            int price,
            int quantity,
            String buyerId,
            String sellerId,
            String buyerStation,
            String sellerStation,
            long timestamp,
            String source,
            String recordedAt
    ) {
        static TradeState from(TradeEvent trade, String source) {
            return new TradeState(
                    trade.id(),
                    trade.goodName(),
                    trade.price(),
                    trade.quantity(),
                    trade.buyerId(),
                    trade.sellerId(),
                    trade.buyerStation(),
                    trade.sellerStation(),
                    trade.timestamp(),
                    source,
                    Instant.now().toString()
            );
        }
    }

    public record StateEvent(
            String type,
            String message,
            String recordedAt
    ) {
    }
}

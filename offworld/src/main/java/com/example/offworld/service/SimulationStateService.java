package com.example.offworld.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.offworld.config.OffworldProperties;
import com.example.offworld.dto.market.MarketOrderDto;
import com.example.offworld.dto.market.OrderBookDto;
import com.example.offworld.dto.market.OrderBookLevel;
import com.example.offworld.dto.market.TradeEvent;
import com.example.offworld.dto.player.PlayerDto;
import com.example.offworld.dto.shipping.ShipDto;
import com.example.offworld.dto.station.StationDto;

@Service
public class SimulationStateService {

    private static final int MAX_RECENT_TRADES = 50;

    private final OffworldProperties props;

    private final Map<String, PlanetState> planets = new ConcurrentHashMap<>();
    private final Map<String, OrderState> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, ShipState> ships = new ConcurrentHashMap<>();
    private final Map<String, MarketPriceState> marketPrices = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<TradeState> recentTrades = new ConcurrentLinkedDeque<>();
    private volatile PlayerState player;

    private final AtomicReference<String> lastUpdatedAt = new AtomicReference<>(Instant.now().toString());

    public SimulationStateService(OffworldProperties props) {
        this.props = props;
    }

    public void updatePlayer(PlayerDto playerDto, String source) {
        if (playerDto == null) {
            return;
        }

        this.player = new PlayerState(
                playerDto.id(),
                playerDto.name(),
                playerDto.credits(),
                source,
                now()
        );

        touch();
    }

    public void updatePlanetInventory(String systemName, String planetId, StationDto station, String source) {
        if (planetId == null || planetId.isBlank() || station == null) {
            return;
        }

        planets.put(
                planetId,
                new PlanetState(
                        planetId,
                        station.name() != null && !station.name().isBlank() ? station.name() : planetId,
                        systemName,
                        copyIntegerMap(station.inventory()),
                        source,
                        now()
                )
        );

        touch();
    }

    public void updateOrder(MarketOrderDto order, String source) {
        if (order == null || order.id() == null || order.id().isBlank()) {
            return;
        }

        if (isTerminalStatus(order.status())) {
            activeOrders.remove(order.id());
        } else {
            activeOrders.put(order.id(), OrderState.from(order, source, now()));
        }

        touch();
    }

    public void removeOrder(String orderId, String source) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }

        activeOrders.remove(orderId);
        touch();
    }

    public void updateShip(ShipDto ship, String source) {
        if (ship == null || ship.id() == null || ship.id().isBlank()) {
            return;
        }

        ships.put(ship.id(), ShipState.from(ship, source, now()));
        touch();
    }

    public void updateOrderBook(OrderBookDto orderBook, String source) {
        if (orderBook == null || orderBook.goodName() == null || orderBook.goodName().isBlank()) {
            return;
        }

        OrderBookLevel bestBid = firstLevel(orderBook.bids());
        OrderBookLevel bestAsk = firstLevel(orderBook.asks());

        marketPrices.put(
                orderBook.goodName(),
                new MarketPriceState(
                        orderBook.goodName(),
                        orderBook.lastTradePrice(),
                        bestBid != null ? bestBid.price() : null,
                        bestAsk != null ? bestAsk.price() : null,
                        (bestBid != null && bestAsk != null) ? bestAsk.price() - bestBid.price() : null,
                        source,
                        now()
                )
        );

        touch();
    }

    public void recordTrade(TradeEvent trade, String source) {
        if (trade == null || trade.id() == null || trade.id().isBlank()) {
            return;
        }

        recentTrades.addFirst(TradeState.from(trade, source));
        while (recentTrades.size() > MAX_RECENT_TRADES) {
            recentTrades.pollLast();
        }

        touch();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", lastUpdatedAt.get());
        result.put("player", player);
        result.put("position", positionOverview());
        result.put("surfaceStock", surfaceStock());
        result.put("globalResources", globalResources());
        result.put("orbitalInventory", orbitalInventory());
        result.put("elevatorOverview", elevatorOverview());
        result.put("spaceShips", spaceShips());
        result.put("operations", operationsOverview());
        result.put("planets", planetStates());
        result.put("ships", shipStates());
        result.put("activeOrders", activeOrderStates());
        result.put("marketPrices", marketPriceStates());
        result.put("recentTrades", recentTradeStates());
        result.put("botStatus", botStatus());
        result.put("counts", Map.of(
                "planets", planets.size(),
                "ships", ships.size(),
                "activeOrders", activeOrders.size(),
                "recentTrades", recentTrades.size(),
                "marketPrices", marketPrices.size()
        ));
        return result;
    }

    public void clearAll() {
        planets.clear();
        activeOrders.clear();
        ships.clear();
        marketPrices.clear();
        recentTrades.clear();
        player = null;
        touch();
    }

    public Map<String, Object> positionOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playerId", props.getPlayerId());
        result.put("baseUrl", props.getBaseUrl());
        result.put("stationSystemName", props.getTrading().getStationSystemName());
        result.put("tradingStationPlanetId", props.getTrading().getStationPlanetId());
        result.put("originSystemName", props.getLogistics().getOriginSystemName());
        result.put("originPlanetId", props.getLogistics().getOriginPlanetId());
        result.put("destinationSystemName", props.getLogistics().getDestinationSystemName());
        result.put("destinationPlanetId", props.getLogistics().getDestinationPlanetId());
        result.put("goodName", props.getLogistics().getGoodName());
        result.put("quantity", props.getLogistics().getQuantity());
        result.put("logisticsEnabled", props.getLogistics().isEnabled());
        result.put("tradingEnabled", props.getTrading().isEnabled());
        result.put("marketEnabled", props.getMarket().isEnabled());
        return result;
    }

    public Map<String, Integer> surfaceStock() {
        String surfacePlanetId = resolveSurfacePlanetId();
        if (surfacePlanetId == null) {
            return Map.of();
        }

        PlanetState planet = planets.get(surfacePlanetId);
        if (planet == null || planet.inventory() == null) {
            return Map.of();
        }

        return sortDesc(planet.inventory());
    }

    public Map<String, Integer> globalResources() {
        Map<String, Integer> totals = new LinkedHashMap<>();

        planets.values().forEach(planet -> {
            if (planet.inventory() == null) {
                return;
            }

            planet.inventory().forEach((good, qty) -> {
                if (good == null || good.isBlank()) {
                    return;
                }
                totals.merge(good, qty == null ? 0 : qty, Integer::sum);
            });
        });

        return sortDesc(totals);
    }

    public Map<String, Integer> orbitalInventory() {
        Map<String, Integer> totals = new LinkedHashMap<>();

        ships.values().forEach(ship -> {
            if (ship.cargo() == null) {
                return;
            }

            ship.cargo().forEach((good, qty) -> {
                if (good == null || good.isBlank()) {
                    return;
                }
                totals.merge(good, qty == null ? 0 : qty, Integer::sum);
            });
        });

        return sortDesc(totals);
    }

    public Map<String, Object> elevatorOverview() {
        Map<String, Object> result = new LinkedHashMap<>();

        String surfacePlanetId = resolveSurfacePlanetId();
        String surfaceSystemName = resolveSurfaceSystemName();
        String goodName = props.getLogistics().getGoodName();
        int requestedQuantity = props.getLogistics().getQuantity();
        int availableSurfaceStock = surfaceStock().getOrDefault(goodName, 0);
        int loadableNow = Math.min(availableSurfaceStock, requestedQuantity);

        result.put("enabled", props.getLogistics().isEnabled());
        result.put("type", "station_to_planet");
        result.put("surfacePlanetId", surfacePlanetId);
        result.put("surfaceSystemName", surfaceSystemName);
        result.put("orbitalNode", "Orbital Hub");
        result.put("goodName", goodName);
        result.put("requestedQuantity", requestedQuantity);
        result.put("availableSurfaceStock", availableSurfaceStock);
        result.put("loadableNow", loadableNow);
        result.put("direction", "surface <-> orbital");

        String status;
        if (!props.getLogistics().isEnabled()) {
            status = "disabled";
        } else if (availableSurfaceStock <= 0) {
            status = "waiting_surface_stock";
        } else if (loadableNow < requestedQuantity) {
            status = "partial_load_ready";
        } else {
            status = "ready";
        }

        result.put("status", status);
        return result;
    }

    public Map<String, ShipState> spaceShips() {
        return ships.values().stream()
                .sorted(Comparator.comparing(ShipState::updatedAt).reversed())
                .collect(Collectors.toMap(
                        ShipState::shipId,
                        s -> s,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Object> operationsOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetPlanet", props.getLogistics().getDestinationPlanetId());
        result.put("contractGood", props.getLogistics().getGoodName());
        result.put("quantity", props.getLogistics().getQuantity());
        result.put("spaceRoute", props.getLogistics().getOriginPlanetId() + " -> " + props.getLogistics().getDestinationPlanetId());
        result.put("surfacePlanet", resolveSurfacePlanetId());
        result.put("spaceShips", ships.size());
        result.put("openOrders", activeOrders.size());
        return result;
    }

    public Map<String, Object> botStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        TradeState lastTrade = recentTrades.peekFirst();
        if (lastTrade != null) {
            result.put("lastAction", "Trade " + lastTrade.goodName() + " x" + lastTrade.quantity() + " @ " + lastTrade.price());
        } else if (!activeOrders.isEmpty()) {
            result.put("lastAction", "Ordres actifs en cours");
        } else if (!ships.isEmpty()) {
            result.put("lastAction", "Transport spatial en cours");
        } else if (props.getLogistics().isEnabled()) {
            result.put("lastAction", "Ascenseur local prêt / surveillance active");
        } else {
            result.put("lastAction", "Aucune action récente");
        }

        result.put("activeOrders", activeOrders.size());
        result.put("trackedMarkets", marketPrices.size());
        result.put("activeSpaceShips", ships.size());

        String bestOpportunity = marketPrices.values().stream()
                .filter(mp -> mp.spread() != null)
                .sorted((a, b) -> Integer.compare(b.spread(), a.spread()))
                .map(mp -> mp.goodName() + " spread=" + mp.spread())
                .findFirst()
                .orElse("Aucune opportunité");

        result.put("bestOpportunity", bestOpportunity);
        return result;
    }

    public Map<String, PlanetState> planetStates() {
        return planets.values().stream()
                .sorted(Comparator.comparing(PlanetState::updatedAt).reversed())
                .collect(Collectors.toMap(
                        PlanetState::planetId,
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public Map<String, ShipState> shipStates() {
        return new LinkedHashMap<>(ships);
    }

    public Map<String, OrderState> activeOrderStates() {
        return new LinkedHashMap<>(activeOrders);
    }

    public Map<String, MarketPriceState> marketPriceStates() {
        return new LinkedHashMap<>(marketPrices);
    }

    public List<TradeState> recentTradeStates() {
        return new ArrayList<>(recentTrades);
    }

    private String resolveSurfacePlanetId() {
        if (props.getTrading().getStationPlanetId() != null && !props.getTrading().getStationPlanetId().isBlank()) {
            return props.getTrading().getStationPlanetId();
        }
        return props.getLogistics().getOriginPlanetId();
    }

    private String resolveSurfaceSystemName() {
        if (props.getTrading().getStationSystemName() != null && !props.getTrading().getStationSystemName().isBlank()) {
            return props.getTrading().getStationSystemName();
        }
        return props.getLogistics().getOriginSystemName();
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        return status.equalsIgnoreCase("filled")
                || status.equalsIgnoreCase("cancelled")
                || status.equalsIgnoreCase("rejected");
    }

    private OrderBookLevel firstLevel(List<OrderBookLevel> levels) {
        return levels == null || levels.isEmpty() ? null : levels.get(0);
    }

    private Map<String, Integer> sortDesc(Map<String, Integer> input) {
        return input.entrySet()
                .stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(
                        LinkedHashMap::new,
                        (map, e) -> map.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll
                );
    }

    private Map<String, Integer> copyIntegerMap(Map<?, ?> source) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }

        source.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            result.put(String.valueOf(key), value instanceof Number n ? n.intValue() : 0);
        });

        return result;
    }

    private void touch() {
        lastUpdatedAt.set(now());
    }

    private String now() {
        return Instant.now().toString();
    }

    public record PlayerState(
            String playerId,
            String name,
            Integer credits,
            String source,
            String updatedAt
            ) {

    }

    public record PlanetState(
            String planetId,
            String displayName,
            String systemName,
            Map<String, Integer> inventory,
            String source,
            String updatedAt
            ) {

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
                    normalizeCargo(ship.cargo()),
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

        private static Map<String, Integer> normalizeCargo(Map<?, ?> source) {
            Map<String, Integer> result = new LinkedHashMap<>();
            if (source == null) {
                return result;
            }

            source.forEach((key, value) -> result.put(String.valueOf(key), value instanceof Number n ? n.intValue() : 0));
            return result;
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
}

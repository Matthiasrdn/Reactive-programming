package com.example.offworld.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "offworld")
public class OffworldProperties {

    private String baseUrl;
    private String playerId;
    private String apiKey;
    private String callbackUrl;
    private Polling polling = new Polling();
    private Market market = new Market();
    private Trading trading = new Trading();
    private Logistics logistics = new Logistics();
    private OrderManagement orderManagement = new OrderManagement();
    private Sync sync = new Sync();

    public static class Polling {

        private Duration shipInterval = Duration.ofSeconds(5);
        private Duration constructionInterval = Duration.ofSeconds(10);

        public Duration getShipInterval() {
            return shipInterval;
        }

        public void setShipInterval(Duration shipInterval) {
            this.shipInterval = shipInterval;
        }

        public Duration getConstructionInterval() {
            return constructionInterval;
        }

        public void setConstructionInterval(Duration constructionInterval) {
            this.constructionInterval = constructionInterval;
        }
    }

    public static class Market {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Trading {

        private boolean enabled = true;
        private String stationSystemName = "Proxima Centauri";
        private String stationPlanetId = "Proxima Centauri-1";
        private int defaultQuantity = 10;
        private int minSpread = 5;
        private List<String> watchedGoods = new ArrayList<>(List.of(
                "food",
                "water",
                "iron_ore",
                "rare_metals"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStationSystemName() {
            return stationSystemName;
        }

        public void setStationSystemName(String stationSystemName) {
            this.stationSystemName = stationSystemName;
        }

        public String getStationPlanetId() {
            return stationPlanetId;
        }

        public void setStationPlanetId(String stationPlanetId) {
            this.stationPlanetId = stationPlanetId;
        }

        public int getDefaultQuantity() {
            return defaultQuantity;
        }

        public void setDefaultQuantity(int defaultQuantity) {
            this.defaultQuantity = defaultQuantity;
        }

        public int getMinSpread() {
            return minSpread;
        }

        public void setMinSpread(int minSpread) {
            this.minSpread = minSpread;
        }

        public List<String> getWatchedGoods() {
            return watchedGoods;
        }

        public void setWatchedGoods(List<String> watchedGoods) {
            this.watchedGoods = watchedGoods;
        }
    }

    public static class Logistics {

        private boolean enabled = true;
        private String originSystemName = "Proxima Centauri";
        private String originPlanetId = "Proxima Centauri-1";
        private String destinationSystemName = "Sol";
        private String destinationPlanetId = "Sol-3";
        private String goodName = "rare_metals";
        private int quantity = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getOriginSystemName() {
            return originSystemName;
        }

        public void setOriginSystemName(String originSystemName) {
            this.originSystemName = originSystemName;
        }

        public String getOriginPlanetId() {
            return originPlanetId;
        }

        public void setOriginPlanetId(String originPlanetId) {
            this.originPlanetId = originPlanetId;
        }

        public String getDestinationSystemName() {
            return destinationSystemName;
        }

        public void setDestinationSystemName(String destinationSystemName) {
            this.destinationSystemName = destinationSystemName;
        }

        public String getDestinationPlanetId() {
            return destinationPlanetId;
        }

        public void setDestinationPlanetId(String destinationPlanetId) {
            this.destinationPlanetId = destinationPlanetId;
        }

        public String getGoodName() {
            return goodName;
        }

        public void setGoodName(String goodName) {
            this.goodName = goodName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static class OrderManagement {

        private boolean enabled = true;
        private int maxOpenOrdersPerGood = 2;
        private long cancelAfterSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxOpenOrdersPerGood() {
            return maxOpenOrdersPerGood;
        }

        public void setMaxOpenOrdersPerGood(int maxOpenOrdersPerGood) {
            this.maxOpenOrdersPerGood = maxOpenOrdersPerGood;
        }

        public long getCancelAfterSeconds() {
            return cancelAfterSeconds;
        }

        public void setCancelAfterSeconds(long cancelAfterSeconds) {
            this.cancelAfterSeconds = cancelAfterSeconds;
        }
    }

    public static class Sync {

        private long stationsIntervalMs = 5000;
        private long shipsIntervalMs = 5000;
        private long ordersIntervalMs = 5000;
        private long marketIntervalMs = 15000;
        private long logisticsIntervalMs = 8000;
        private long tradingScanIntervalMs = 15000;

        public long getStationsIntervalMs() {
            return stationsIntervalMs;
        }

        public void setStationsIntervalMs(long stationsIntervalMs) {
            this.stationsIntervalMs = stationsIntervalMs;
        }

        public long getShipsIntervalMs() {
            return shipsIntervalMs;
        }

        public void setShipsIntervalMs(long shipsIntervalMs) {
            this.shipsIntervalMs = shipsIntervalMs;
        }

        public long getOrdersIntervalMs() {
            return ordersIntervalMs;
        }

        public void setOrdersIntervalMs(long ordersIntervalMs) {
            this.ordersIntervalMs = ordersIntervalMs;
        }

        public long getMarketIntervalMs() {
            return marketIntervalMs;
        }

        public void setMarketIntervalMs(long marketIntervalMs) {
            this.marketIntervalMs = marketIntervalMs;
        }

        public long getLogisticsIntervalMs() {
            return logisticsIntervalMs;
        }

        public void setLogisticsIntervalMs(long logisticsIntervalMs) {
            this.logisticsIntervalMs = logisticsIntervalMs;
        }

        public long getTradingScanIntervalMs() {
            return tradingScanIntervalMs;
        }

        public void setTradingScanIntervalMs(long tradingScanIntervalMs) {
            this.tradingScanIntervalMs = tradingScanIntervalMs;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Polling getPolling() {
        return polling;
    }

    public void setPolling(Polling polling) {
        this.polling = polling;
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
    }

    public Trading getTrading() {
        return trading;
    }

    public void setTrading(Trading trading) {
        this.trading = trading;
    }

    public Logistics getLogistics() {
        return logistics;
    }

    public void setLogistics(Logistics logistics) {
        this.logistics = logistics;
    }

    public OrderManagement getOrderManagement() {
        return orderManagement;
    }

    public void setOrderManagement(OrderManagement orderManagement) {
        this.orderManagement = orderManagement;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }
}

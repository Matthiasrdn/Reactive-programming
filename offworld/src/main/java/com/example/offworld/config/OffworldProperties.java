package com.example.offworld.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "offworld")
public class OffworldProperties {

    private String baseUrl;
    private String playerId;
    private String apiKey;
    private String callbackUrl;
    private Polling polling = new Polling();
    private Market market = new Market();

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

    private Trading trading = new Trading();

    public static class Trading {

        private boolean enabled = true;
        private String stationPlanetId = "Sol-3";
        private int defaultQuantity = 10;
        private int minSpread = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    public Trading getTrading() {
        return trading;
    }

    public void setTrading(Trading trading) {
        this.trading = trading;
    }
}

package com.example.offworld.dto.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarketOrderDto(
        String id,
        @JsonProperty("player_id")
        String playerId,
        @JsonProperty("good_name")
        String goodName,
        String side,
        @JsonProperty("order_type")
        String orderType,
        Integer price,
        int quantity,
        @JsonProperty("filled_quantity")
        int filledQuantity,
        String status,
        @JsonProperty("station_planet_id")
        String stationPlanetId,
        @JsonProperty("created_at")
        long createdAt
        ) {

}

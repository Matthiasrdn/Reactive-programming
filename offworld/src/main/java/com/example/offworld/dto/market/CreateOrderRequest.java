package com.example.offworld.dto.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrderRequest(
        @JsonProperty("good_name")
        String goodName,
        String side,
        @JsonProperty("order_type")
        String orderType,
        Integer price,
        int quantity,
        @JsonProperty("station_planet_id")
        String stationPlanetId
        ) {

}

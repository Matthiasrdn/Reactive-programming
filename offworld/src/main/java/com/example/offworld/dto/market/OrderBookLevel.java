package com.example.offworld.dto.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderBookLevel(
        int price,
        @JsonProperty("total_quantity")
        int totalQuantity,
        @JsonProperty("order_count")
        int orderCount
        ) {

}

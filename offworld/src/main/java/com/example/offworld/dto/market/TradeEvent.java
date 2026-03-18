package com.example.offworld.dto.market;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TradeEvent(
        String id,
        @JsonProperty("good_name")
        String goodName,
        int price,
        int quantity,
        @JsonProperty("buyer_id")
        String buyerId,
        @JsonProperty("seller_id")
        String sellerId,
        @JsonProperty("buyer_station")
        String buyerStation,
        @JsonProperty("seller_station")
        String sellerStation,
        long timestamp
        ) {

}

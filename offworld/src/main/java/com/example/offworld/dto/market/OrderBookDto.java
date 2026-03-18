package com.example.offworld.dto.market;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderBookDto(
        @JsonProperty("good_name")
        String goodName,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        @JsonProperty("last_trade_price")
        Integer lastTradePrice
        ) {

}

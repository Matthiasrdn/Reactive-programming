package com.example.offworld.dto.player;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerDto(
        String id,
        String name,
        int credits,
        @JsonProperty("callback_url")
        String callbackUrl
        ) {

}

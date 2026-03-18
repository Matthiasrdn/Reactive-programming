package com.example.offworld.dto.player;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdatePlayerRequest(
        @JsonProperty("callback_url")
        String callbackUrl
        ) {

}

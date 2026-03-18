package com.example.offworld.dto.station;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StationDto(
        String id,
        @JsonProperty("planet_id")
        String planetId,
        Map<String, Integer> storage,
        @JsonProperty("max_storage")
        int maxStorage
        ) {

}

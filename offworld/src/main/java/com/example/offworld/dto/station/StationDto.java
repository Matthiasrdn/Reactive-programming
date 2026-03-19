package com.example.offworld.dto.station;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StationDto(
        String name,
        @JsonProperty("owner_id")
        String ownerId,
        Map<String, Integer> inventory
        ) {

}

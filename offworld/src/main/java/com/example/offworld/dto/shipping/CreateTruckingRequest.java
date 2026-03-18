package com.example.offworld.dto.shipping;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTruckingRequest(
        @JsonProperty("origin_planet_id")
        String originPlanetId,
        @JsonProperty("destination_planet_id")
        String destinationPlanetId,
        Map<String, Integer> cargo
        ) {

}

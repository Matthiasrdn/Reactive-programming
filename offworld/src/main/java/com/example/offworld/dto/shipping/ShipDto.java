package com.example.offworld.dto.shipping;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShipDto(
        String id,
        @JsonProperty("owner_id")
        String ownerId,
        @JsonProperty("origin_planet_id")
        String originPlanetId,
        @JsonProperty("destination_planet_id")
        String destinationPlanetId,
        Map<String, Integer> cargo,
        String status,
        @JsonProperty("trucking_id")
        String truckingId,
        Integer fee,
        @JsonProperty("created_at")
        Long createdAt,
        @JsonProperty("arrival_at")
        Long arrivalAt,
        @JsonProperty("operation_complete_at")
        Long operationCompleteAt
        ) {

}

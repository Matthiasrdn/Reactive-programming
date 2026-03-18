package com.example.offworld.dto.webhook;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookEvent(
        String event,
        @JsonProperty("ship_id")
        String shipId,
        @JsonProperty("origin_planet_id")
        String originPlanetId,
        @JsonProperty("destination_planet_id")
        String destinationPlanetId,
        Map<String, Integer> cargo,
        String status,
        @JsonProperty("project_id")
        String projectId
        ) {

}

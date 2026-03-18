package com.example.offworld.dto.station;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StationDto(
        String name,
        @JsonProperty("owner_id")
        String ownerId,
        Map<String, Integer> inventory,
        @JsonProperty("docking_bays")
        int dockingBays,
        @JsonProperty("max_storage")
        int maxStorage
        ) {

}

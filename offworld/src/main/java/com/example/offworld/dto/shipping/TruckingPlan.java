package com.example.offworld.dto.shipping;

public record TruckingPlan(
        String originSystemName,
        String originPlanetId,
        String destinationPlanetId,
        String goodName,
        int quantity
        ) {

}

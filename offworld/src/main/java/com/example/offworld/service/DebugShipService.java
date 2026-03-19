package com.example.offworld.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.offworld.dto.shipping.ShipDto;

@Service
public class DebugShipService {

    private final Map<String, Map<String, Object>> ships = new ConcurrentHashMap<>();

    public void recordShipSnapshot(ShipDto ship, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shipId", ship.id());
        data.put("ownerId", ship.ownerId());
        data.put("originPlanetId", ship.originPlanetId());
        data.put("destinationPlanetId", ship.destinationPlanetId());
        data.put("status", ship.status());
        data.put("cargo", ship.cargo());
        data.put("truckingId", ship.truckingId());
        data.put("fee", ship.fee());
        data.put("createdAt", ship.createdAt());
        data.put("arrivalAt", ship.arrivalAt());
        data.put("operationCompleteAt", ship.operationCompleteAt());
        data.put("note", note);
        data.put("updatedAt", Instant.now().toString());

        ships.put(ship.id(), data);
    }

    public void recordShipNote(String shipId, String note) {
        Map<String, Object> existing = ships.computeIfAbsent(shipId, id -> new LinkedHashMap<>());
        existing.put("shipId", shipId);
        existing.put("note", note);
        existing.put("updatedAt", Instant.now().toString());
    }

    public Map<String, Object> getShip(String shipId) {
        return ships.getOrDefault(shipId, Map.of(
                "shipId", shipId,
                "note", "Aucune donnée pour ce ship",
                "updatedAt", Instant.now().toString()
        ));
    }

    public Map<String, Object> getAllShips() {
        return new LinkedHashMap<>(ships);
    }
}

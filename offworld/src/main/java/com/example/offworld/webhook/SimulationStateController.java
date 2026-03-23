package com.example.offworld.webhook;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.offworld.service.SimulationStateService;

import reactor.core.publisher.Flux;

@RestController
public class SimulationStateController {

    private final SimulationStateService simulationStateService;

    public SimulationStateController(SimulationStateService simulationStateService) {
        this.simulationStateService = simulationStateService;
    }

    @GetMapping(path = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> state() {
        return simulationStateService.snapshot();
    }

    @GetMapping(path = "/stream/state", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamState() {
        return Flux.interval(Duration.ofSeconds(1))
                .startWith(0L)
                .map(tick -> simulationStateService.snapshot());
    }
}

package com.example.offworld.service;

import com.example.offworld.api.PlayerClient;
import com.example.offworld.config.OffworldProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PlayerSyncService {

    private final PlayerClient playerClient;
    private final OffworldProperties props;
    private final SimulationStateService simulationStateService;

    public PlayerSyncService(
            PlayerClient playerClient,
            OffworldProperties props,
            SimulationStateService simulationStateService
    ) {
        this.playerClient = playerClient;
        this.props = props;
        this.simulationStateService = simulationStateService;
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void syncPlayer() {
        playerClient.getPlayer(props.getPlayerId())
                .doOnNext(player -> simulationStateService.updatePlayer(player, "sync:player"))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}

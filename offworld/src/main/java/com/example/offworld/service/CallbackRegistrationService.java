package com.example.offworld.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.offworld.api.PlayerClient;
import com.example.offworld.config.OffworldProperties;

import jakarta.annotation.PostConstruct;

@Service
public class CallbackRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistrationService.class);

    private final PlayerClient playerClient;
    private final OffworldProperties props;

    public CallbackRegistrationService(PlayerClient playerClient, OffworldProperties props) {
        this.playerClient = playerClient;
        this.props = props;
    }

    @PostConstruct
    public void registerCallback() {
        log.warn("Callback désactivée temporairement (bug serveur)");
    }
}

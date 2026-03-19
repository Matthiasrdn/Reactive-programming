package com.example.offworld.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.offworld.dto.webhook.WebhookEvent;
import com.example.offworld.service.DebugStateService;
import com.example.offworld.service.ShipAutomationService;

import reactor.core.publisher.Mono;

@Component
public class WebhookEventRouter {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventRouter.class);

    private final ShipAutomationService shipAutomationService;
    private final DebugStateService debugStateService;

    public WebhookEventRouter(
            ShipAutomationService shipAutomationService,
            DebugStateService debugStateService
    ) {
        this.shipAutomationService = shipAutomationService;
        this.debugStateService = debugStateService;
    }

    public Mono<Void> route(WebhookEvent event) {
        debugStateService.recordWebhook(event.toString());
        log.info("Webhook reçu: {}", event);

        return switch (event.event()) {
            case "OriginDockingRequest", "DockingRequest", "ShipDocked", "ShipComplete" ->
                shipAutomationService.onWebhook(event);
            case "ConstructionComplete" -> {
                log.info("Construction terminée: {}", event.projectId());
                yield Mono.empty();
            }
            default -> {
                log.warn("Event webhook inconnu: {}", event.event());
                yield Mono.empty();
            }
        };
    }
}

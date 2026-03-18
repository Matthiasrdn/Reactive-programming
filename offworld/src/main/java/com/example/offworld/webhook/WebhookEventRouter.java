package com.example.offworld.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.offworld.dto.webhook.WebhookEvent;
import com.example.offworld.service.ShipAutomationService;

import reactor.core.publisher.Mono;

@Component
public class WebhookEventRouter {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventRouter.class);

    private final ShipAutomationService shipAutomationService;

    public WebhookEventRouter(ShipAutomationService shipAutomationService) {
        this.shipAutomationService = shipAutomationService;
    }

    public Mono<Void> route(WebhookEvent event) {
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

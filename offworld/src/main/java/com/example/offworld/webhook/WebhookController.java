package com.example.offworld.webhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.offworld.dto.webhook.WebhookEvent;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookEventRouter router;

    public WebhookController(WebhookEventRouter router) {
        this.router = router;
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> handle(@RequestBody WebhookEvent event) {
        return router.route(event)
                .thenReturn(ResponseEntity.ok().build());
    }
}

package com.example.offworld.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.offworld.service.DebugStateService;
import com.example.offworld.support.ApiException;

import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    WebClient offworldWebClient(OffworldProperties props, DebugStateService debugStateService) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .filter(logRequest(debugStateService))
                .filter(logResponse(debugStateService))
                .filter(errorHandlingFilter())
                .build();
    }

    private ExchangeFilterFunction logRequest(DebugStateService debugStateService) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String value = request.method() + " " + request.url();
            debugStateService.recordHttpRequest(value);
            log.info("HTTP {} {}", request.method(), request.url());

            String auth = request.headers().getFirst(HttpHeaders.AUTHORIZATION);
            if (auth != null) {
                log.debug("Authorization header présent");
            }

            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse(DebugStateService debugStateService) {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            String value = response.statusCode().toString();
            debugStateService.recordHttpResponse(value);
            log.info("HTTP RESPONSE {}", response.statusCode());
            return Mono.just(response);
        });
    }

    private ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            HttpStatusCode status = response.statusCode();

            if (!status.isError()) {
                return Mono.just(response);
            }

            return response.bodyToMono(String.class)
                    .defaultIfEmpty("Unknown error")
                    .flatMap(body -> Mono.error(new ApiException(status.value(), body)));
        });
    }
}

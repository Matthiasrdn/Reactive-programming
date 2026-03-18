package com.example.offworld.config;

import com.example.offworld.support.ApiException;
import com.example.offworld.support.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient offworldWebClient(OffworldProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .filter(errorHandlingFilter())
                .build();
    }

    private ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            HttpStatusCode status = response.statusCode();

            if (!status.isError()) {
                return Mono.just(response);
            }

            return response.bodyToMono(ErrorResponse.class)
                    .defaultIfEmpty(new ErrorResponse("Unknown error"))
                    .flatMap(error -> Mono.error(new ApiException(status.value(), error.error())));
        });
    }
}

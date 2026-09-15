package com.srm.creditengine.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean do RestClient (cliente HTTP síncrono do Spring 6+), usado pelo
 * ApiExchangeRateProvider (Passo 8) para consultar a API pública de câmbio.
 *
 * Timeout curto configurado aqui de propósito (SPEC.md Seção 5: "timeout
 * curto por chamada + retry com backoff exponencial") -- se o provedor
 * demorar mais que isso, a chamada falha rápido e o Resilience4j (retry/
 * circuit breaker) assume o controle, em vez de deixar a requisição HTTP
 * do operador esperando indefinidamente.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient exchangeRateRestClient() {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl("https://open.er-api.com/v6")
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}

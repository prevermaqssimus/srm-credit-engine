package com.srm.creditengine.currency.provider;

import com.srm.creditengine.domain.enums.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * Teste unitário do ApiExchangeRateProvider, ISOLADO de rede real -- usa
 * Mockito para simular a resposta da API (recomendação do próprio plano:
 * "testável isoladamente com WireMock/Mockito").
 *
 * IMPORTANTE: como este teste NÃO sobe contexto Spring (@SpringBootTest),
 * ele NÃO valida o comportamento de @Retry/@CircuitBreaker do Resilience4j
 * de verdade -- essas anotações só têm efeito quando o Spring cria um proxy
 * em volta do bean, o que só acontece com contexto Spring real. Este teste
 * valida a LÓGICA de parsing/validação da resposta e o tratamento de erro
 * dentro do método, não o comportamento de retry/circuito em si.
 *
 * Uso de Clock fixo (Passo 6): garante que o timestamp retornado seja
 * sempre exatamente previsível, sem depender do momento real da execução.
 */
class ApiExchangeRateProviderTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Retorna a taxa corretamente quando a API responde com sucesso")
    void getCurrentRate_returnsRate_whenApiRespondsSuccessfully() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        ExchangeRateApiResponse mockResponse =
                new ExchangeRateApiResponse("success", "USD", Map.of("BRL", 5.4321));

        when(restClient.get()
                .uri("/latest/{from}", "USD")
                .retrieve()
                .body(ExchangeRateApiResponse.class))
                .thenReturn(mockResponse);

        ApiExchangeRateProvider provider = new ApiExchangeRateProvider(restClient, fixedClock);

        ExchangeRateResult result = provider.getCurrentRate(Currency.USD, Currency.BRL);

        assertThat(result.rate()).isEqualByComparingTo("5.4321");
        assertThat(result.obtainedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(result.providerName()).isEqualTo("open.er-api.com");
    }

    @Test
    @DisplayName("Lança ExchangeRateUnavailableException quando a API retorna 'result != success'")
    // SPEC.md Seção 5: nunca retornar taxa desatualizada ou "de segurança"
    // silenciosa -- uma resposta malformada deve virar erro explícito.
    void getCurrentRate_throwsException_whenApiResultIsNotSuccess() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        ExchangeRateApiResponse mockResponse = new ExchangeRateApiResponse("error", "USD", Map.of());

        when(restClient.get()
                .uri("/latest/{from}", "USD")
                .retrieve()
                .body(ExchangeRateApiResponse.class))
                .thenReturn(mockResponse);

        ApiExchangeRateProvider provider = new ApiExchangeRateProvider(restClient, fixedClock);

        assertThatThrownBy(() -> provider.getCurrentRate(Currency.USD, Currency.BRL))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    @DisplayName("Lança ExchangeRateUnavailableException quando o par de moedas não está na resposta")
    void getCurrentRate_throwsException_whenCurrencyPairNotInResponse() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        // Resposta de sucesso, mas sem a moeda BRL nas taxas retornadas.
        ExchangeRateApiResponse mockResponse =
                new ExchangeRateApiResponse("success", "USD", Map.of("EUR", 0.91));

        when(restClient.get()
                .uri("/latest/{from}", "USD")
                .retrieve()
                .body(ExchangeRateApiResponse.class))
                .thenReturn(mockResponse);

        ApiExchangeRateProvider provider = new ApiExchangeRateProvider(restClient, fixedClock);

        assertThatThrownBy(() -> provider.getCurrentRate(Currency.USD, Currency.BRL))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    @DisplayName("Lança ExchangeRateUnavailableException quando a API retorna corpo nulo")
    void getCurrentRate_throwsException_whenApiReturnsNullBody() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

        when(restClient.get()
                .uri("/latest/{from}", "USD")
                .retrieve()
                .body(ExchangeRateApiResponse.class))
                .thenReturn(null);

        ApiExchangeRateProvider provider = new ApiExchangeRateProvider(restClient, fixedClock);

        assertThatThrownBy(() -> provider.getCurrentRate(Currency.USD, Currency.BRL))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }
}

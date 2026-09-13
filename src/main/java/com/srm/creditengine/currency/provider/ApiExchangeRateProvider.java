package com.srm.creditengine.currency.provider;

import com.srm.creditengine.domain.enums.Currency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Tradução: ApiProvedorTaxaCambio (plano original) → ApiExchangeRateProvider.
 *
 * Implementação real do ExchangeRateProvider (Passo 7), consultando a API
 * pública https://open.er-api.com -- adequada ao escopo deste case (ver
 * DECISIONS.md e SPEC.md Seção 2, pergunta 1: em produção real, seria
 * substituída por um provedor com SLA/governança, ex: Bloomberg/BCB).
 *
 * Resiliência (SPEC.md Seção 5):
 *   - @Retry: tenta novamente em caso de falha transitória (timeout, erro
 *     de rede), com backoff exponencial -- configuração em
 *     application.properties (resilience4j.retry.instances.exchangeRateProvider).
 *   - @CircuitBreaker: se as falhas se acumularem além do limiar
 *     configurado, o circuito "abre" e passa a rejeitar chamadas
 *     imediatamente (sem nem tentar a rede) por um tempo, evitando
 *     sobrecarregar um provedor já degradado.
 *   - fallback: se, mesmo com retry e circuit breaker, a chamada não for
 *     bem-sucedida, lança ExchangeRateUnavailableException -- nunca
 *     retorna uma taxa desatualizada ou um valor "de segurança" silencioso
 *     (regra inegociável da SPEC.md).
 *
 * Nota de escopo (registrar em DECISIONS.md): esta versão usa UM único
 * provedor real. A SPEC.md Seção 5 descreve um modelo com DOIS provedores
 * (primário + secundário) -- essa evolução fica para um passo futuro
 * (documentado como próximo passo, não implementado agora).
 */
@Component
public class ApiExchangeRateProvider implements ExchangeRateProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiExchangeRateProvider.class);
    private static final String PROVIDER_NAME = "open.er-api.com";

    private final RestClient restClient;
    private final Clock clock;

    public ApiExchangeRateProvider(RestClient exchangeRateRestClient, Clock clock) {
        this.restClient = exchangeRateRestClient;
        this.clock = clock;
    }

    @Override
    @Retry(name = "exchangeRateProvider", fallbackMethod = "onFailure")
    @CircuitBreaker(name = "exchangeRateProvider", fallbackMethod = "onFailure")
    public ExchangeRateResult getCurrentRate(Currency from, Currency to) {
        log.info("exchange_rate_request from={} to={} provider={}", from, to, PROVIDER_NAME);

        ExchangeRateApiResponse response = restClient.get()
                .uri("/latest/{from}", from.name())
                .retrieve()
                .body(ExchangeRateApiResponse.class);

        if (response == null || !"success".equals(response.result())) {
            throw new ExchangeRateUnavailableException(
                    "Resposta inválida do provedor de câmbio " + PROVIDER_NAME);
        }

        Double rateValue = response.rates().get(to.name());
        if (rateValue == null) {
            throw new ExchangeRateUnavailableException(
                    "Par de moedas não suportado pelo provedor: " + from + "/" + to);
        }

        BigDecimal rate = BigDecimal.valueOf(rateValue);
        Instant now = Instant.now(clock);

        log.info("exchange_rate_success from={} to={} rate={} provider={}", from, to, rate, PROVIDER_NAME);
        return new ExchangeRateResult(rate, now, PROVIDER_NAME);
    }

    /**
     * Fallback chamado pelo Resilience4j quando o retry se esgota OU o
     * circuit breaker está aberto. Assinatura exigida pelo framework: mesmos
     * parâmetros do método original + a exceção que causou a falha.
     *
     * NUNCA retorna uma taxa "de segurança" -- sempre propaga o erro
     * explicitamente, conforme SPEC.md Seção 5.
     */
    private ExchangeRateResult onFailure(Currency from, Currency to, Throwable t) {
        log.error("exchange_rate_failure from={} to={} provider={} error={}",
                from, to, PROVIDER_NAME, t.getMessage());
        throw new ExchangeRateUnavailableException(
                "Provedor de câmbio " + PROVIDER_NAME + " indisponível para " + from + "/" + to, t);
    }
}

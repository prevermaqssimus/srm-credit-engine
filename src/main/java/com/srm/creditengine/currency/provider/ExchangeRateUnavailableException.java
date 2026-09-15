package com.srm.creditengine.currency.provider;

/**
 * Lançada quando o provedor de câmbio não responde (falha de rede, timeout,
 * circuit breaker aberto) mesmo após as tentativas de retry.
 *
 * SPEC.md Seção 5: "a liquidação é rejeitada com erro explícito... não há
 * reversão silenciosa para taxa desatualizada em cache, nem intervenção
 * manual no caminho crítico" -- esta exceção é o mecanismo que garante essa
 * rejeição explícita.
 *
 * Nota: esta exceção ainda não está conectada a um GlobalExceptionHandler
 * (isso é o Passo 14). Por enquanto, ela se propaga como uma
 * RuntimeException comum -- quando o Passo 14 chegar, será mapeada para um
 * status HTTP semântico (422 ou 503).
 */
public class ExchangeRateUnavailableException extends RuntimeException {
    public ExchangeRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExchangeRateUnavailableException(String message) {
        super(message);
    }
}

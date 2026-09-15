package com.srm.creditengine.currency.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Representa o corpo JSON de resposta da API pública usada neste case
 * (https://open.er-api.com) -- ver DECISIONS.md sobre a escolha desta API
 * (adequada ao escopo do case, sem SLA/governança de produção real,
 * conforme reconhecido na própria SPEC.md Seção 2, pergunta 1).
 *
 * Exemplo de resposta real da API (campos irrelevantes omitidos):
 *   { "result": "success", "base_code": "USD", "rates": { "BRL": 5.43, ... } }
 *
 * @JsonProperty mapeia o campo snake_case do JSON ("base_code") para o
 * nome de campo Java em camelCase (convenção do projeto), sem precisar
 * configurar uma estratégia de naming global do Jackson.
 */
record ExchangeRateApiResponse(
        String result,
        @JsonProperty("base_code") String baseCode,
        Map<String, Double> rates
) {
}

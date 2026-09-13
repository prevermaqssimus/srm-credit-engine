package com.srm.creditengine.currency.provider;

import com.srm.creditengine.domain.enums.Currency;

/**
 * Tradução: ProvedorTaxaCambio (plano original) → ExchangeRateProvider.
 *
 * Porta (interface) do Currency Engine -- abstrai QUALQUER fonte de taxa de
 * câmbio. Nada no domínio de precificação/liquidação depende de uma API
 * concreta (SOLID "D" -- Dependency Inversion): quem consome esta interface
 * não sabe (nem precisa saber) se a implementação por trás é uma chamada
 * HTTP real, um mock, ou uma leitura de banco.
 *
 * Passo 7 do plano: só a abstração. Implementação real vem no Passo 8.
 *
 * SPEC.md Seção 5 (Resiliência do Currency Engine): esta porta é o ponto
 * de extensão que permitirá, no futuro, ter dois provedores (primário +
 * secundário) com circuit breaker -- a interface não muda quando isso for
 * implementado, só surgem novas classes implementando-a.
 */
public interface ExchangeRateProvider {

    /**
     * Obtém a taxa de câmbio vigente no momento da chamada, para o par de
     * moedas informado.
     *
     * @param from moeda de origem (ex: USD)
     * @param to moeda de destino (ex: BRL)
     * @return o resultado com taxa, timestamp e nome do provedor que respondeu
     */
    ExchangeRateResult getCurrentRate(Currency from, Currency to);
}

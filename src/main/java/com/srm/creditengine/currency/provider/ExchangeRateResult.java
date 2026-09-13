package com.srm.creditengine.currency.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tradução: TaxaCambioResultado (plano original) → ExchangeRateResult.
 *
 * Carrega o resultado de uma consulta de câmbio: a taxa em si, o instante
 * exato em que foi obtida, e qual provedor respondeu.
 *
 * SPEC.md Critério de Aceite 4 (Auditabilidade): "taxa de câmbio efetiva,
 * PROVEDOR QUE RESPONDEU... Alterar uma liquidação registrada não é uma
 * operação do sistema" -- é por isso que o nome do provedor é parte deste
 * resultado, não um detalhe descartável: quando a liquidação for persistida
 * (passos futuros), esse dado vai junto no registro imutável.
 *
 * Record (imutável) -- um resultado de câmbio nunca deveria ser alterado
 * depois de obtido; se a taxa mudar, é uma NOVA consulta, não um update
 * deste objeto.
 */
public record ExchangeRateResult(
        BigDecimal rate,
        Instant obtainedAt,
        String providerName
) {
}

package com.srm.creditengine.dto;

import com.srm.creditengine.domain.enums.Currency;

import java.math.BigDecimal;

/**
 * Resultado de uma simulação de valor líquido -- espelha os mesmos campos
 * financeiros de Settlement, mas nunca é persistido: é só uma prévia do
 * que a liquidação real produziria se confirmada agora, com a taxa de
 * câmbio vigente NESTE instante (que pode mudar até a liquidação de fato
 * acontecer -- por isso "simulação", não "cotação garantida").
 */
public record SimulationResult(
        BigDecimal presentValueBrl,
        BigDecimal discountBrl,
        BigDecimal finalAmount,
        Currency currency,
        BigDecimal fxRateUsed
) {
}

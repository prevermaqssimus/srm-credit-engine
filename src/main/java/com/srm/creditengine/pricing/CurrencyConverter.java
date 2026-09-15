package com.srm.creditengine.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Tradução: ConversorCambial (plano original) → CurrencyConverter.
 *
 * Responsabilidade única (SOLID "S"): aplicar a conversão cross-currency
 * seguindo EXATAMENTE as duas etapas de arredondamento definidas na
 * SPEC.md Seção 1 -- separado do PricingStrategy (que só resolve o spread)
 * e do PricingService (que orquestra o cálculo em BRL).
 *
 * Regra de negócio (SPEC.md Seção 1, não detalhe livre de implementação):
 *   "Operações cross-currency (BRL → USD): arredondamento ocorre em DUAS
 *    etapas: (1) o valor presente em BRL é calculado com precisão
 *    estendida e arredondado para 2 casas ANTES da conversão; (2) o valor
 *    já convertido para USD é arredondado novamente para 2 casas. Essa
 *    sequência é a que reproduz o golden case C3."
 *
 * Passo 9 do plano: extrai essa lógica, que antes vivia dentro do
 * PricingService, para uma classe dedicada -- separa "calcular o valor em
 * BRL" de "converter esse valor para outra moeda", que são duas
 * responsabilidades distintas.
 */
@Service
public class CurrencyConverter {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private static final MathContext INTERMEDIATE_PRECISION = new MathContext(50);

    /**
     * Converte um valor JÁ ARREDONDADO em BRL (etapa 1, feita pelo
     * PricingService) para a moeda de destino, aplicando a etapa 2 do
     * arredondamento (regra do golden case C3).
     *
     * @param presentValueBrlRounded valor presente em BRL, já arredondado
     *                                para 2 casas (etapa 1)
     * @param fxRateBrlPerUsd taxa de câmbio: quantos BRL valem 1 unidade da
     *                        moeda de destino (ex: par USD/BRL)
     * @return valor convertido, arredondado para 2 casas (etapa 2)
     */
    public BigDecimal convert(BigDecimal presentValueBrlRounded, BigDecimal fxRateBrlPerUsd) {
        return presentValueBrlRounded
                .divide(fxRateBrlPerUsd, INTERMEDIATE_PRECISION)
                .setScale(SCALE, ROUNDING_MODE);
    }
}

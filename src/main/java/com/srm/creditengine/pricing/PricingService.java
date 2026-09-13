package com.srm.creditengine.pricing;

import com.srm.creditengine.config.PricingProperties;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.pricing.strategy.PricingStrategy;
import com.srm.creditengine.pricing.strategy.PricingStrategyFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Tradução: MotorPrecificacao (plano original) → PricingService.
 *
 * Formula (fixa pelo enunciado):
 *   Valor Presente = Valor de Face / (1 + Taxa Base + Spread) ^ Prazo
 *
 * Regras de arredondamento (SPEC.md Secao 1):
 *   - Calculos intermediarios usam MathContext de alta precisao (nunca double/float).
 *   - BRL: arredonda-se 1 UNICA VEZ, no valor presente final, HALF_EVEN, 2 casas.
 *   - Cross-currency (BRL -> USD): arredonda-se em DUAS etapas -- (1) valor
 *     presente em BRL arredondado ANTES da conversao, (2) valor convertido
 *     arredondado NOVAMENTE. Regra de negocio (reproduz golden case C3),
 *     nao detalhe livre de implementacao.
 *
 * Responsabilidade unica (S de SOLID): orquestrar o CALCULO, delegando a
 * decisao de qual spread usar para a PricingStrategyFactory -- nunca decide
 * regra de negocio de spread aqui dentro (isso pertence as strategies).
 */
@Service
public class PricingService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private static final MathContext INTERMEDIATE_PRECISION = new MathContext(50);

    private final PricingStrategyFactory strategyFactory;
    private final BigDecimal baseRate;

    public PricingService(PricingStrategyFactory strategyFactory, PricingProperties pricingProperties) {
        this.strategyFactory = strategyFactory;
        this.baseRate = pricingProperties.baseRate();
    }

    /**
     * Calcula o valor presente em BRL (moeda nativa do titulo), ja
     * arredondado para 2 casas (HALF_EVEN).
     */
    public PricingResult calculatePresentValueBrl(ReceivableType type, BigDecimal faceValue, int termMonths) {
        if (termMonths < 1) {
            throw new IllegalArgumentException("Prazo deve ser de ao menos 1 mes");
        }
        PricingStrategy strategy = strategyFactory.resolve(type);
        BigDecimal spread = strategy.getSpread();
        BigDecimal totalRate = baseRate.add(spread);

        BigDecimal onePlusRate = BigDecimal.ONE.add(totalRate, INTERMEDIATE_PRECISION);
        BigDecimal denominator = onePlusRate.pow(termMonths, INTERMEDIATE_PRECISION);

        BigDecimal presentValueRaw = faceValue.divide(denominator, INTERMEDIATE_PRECISION);
        BigDecimal presentValueRounded = presentValueRaw.setScale(SCALE, ROUNDING_MODE);

        BigDecimal discount = faceValue.setScale(SCALE, ROUNDING_MODE).subtract(presentValueRounded);

        return new PricingResult(presentValueRounded, discount, spread, baseRate);
    }

    /**
     * Converte um valor ja arredondado em BRL para USD, aplicando o
     * arredondamento de segunda etapa (regra do golden case C3).
     */
    public BigDecimal convertToUsd(BigDecimal presentValueBrlRounded, BigDecimal fxRateBrlPerUsd) {
        return presentValueBrlRounded.divide(fxRateBrlPerUsd, INTERMEDIATE_PRECISION)
                .setScale(SCALE, ROUNDING_MODE);
    }

    /** Resultado do calculo, com snapshot dos parametros usados (para auditoria). */
    public record PricingResult(BigDecimal presentValueBrl, BigDecimal discountBrl,
                                 BigDecimal spreadApplied, BigDecimal baseRateApplied) {
    }
}

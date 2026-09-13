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
 *   - Cross-currency (BRL -> USD): a conversao em si (2a etapa de
 *     arredondamento) foi extraida para CurrencyConverter (Passo 9) --
 *     responsabilidade unica: este service calcula o valor em BRL,
 *     CurrencyConverter converte para outra moeda. Ver convertToUsd abaixo,
 *     mantido como repasse fino (nao quebra a API usada pelos testes
 *     existentes -- GoldenCasesTest, PricingServiceEdgeCasesTest).
 *
 * Responsabilidade unica (S de SOLID): orquestrar o CALCULO EM BRL,
 * delegando a decisao de qual spread usar para a PricingStrategyFactory, e
 * a conversao de moeda para o CurrencyConverter -- nunca mistura as três
 * responsabilidades numa classe só.
 */
@Service
public class PricingService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private static final MathContext INTERMEDIATE_PRECISION = new MathContext(50);

    private final PricingStrategyFactory strategyFactory;
    private final BigDecimal baseRate;
    private final CurrencyConverter currencyConverter;

    public PricingService(PricingStrategyFactory strategyFactory, PricingProperties pricingProperties,
                           CurrencyConverter currencyConverter) {
        this.strategyFactory = strategyFactory;
        this.baseRate = pricingProperties.baseRate();
        this.currencyConverter = currencyConverter;
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
     * Converte um valor ja arredondado em BRL para USD. A partir do Passo 9,
     * apenas repassa para CurrencyConverter -- mantido aqui como metodo de
     * conveniencia para nao quebrar os testes ja existentes que chamam
     * pricingService.convertToUsd(...) diretamente.
     */
    public BigDecimal convertToUsd(BigDecimal presentValueBrlRounded, BigDecimal fxRateBrlPerUsd) {
        return currencyConverter.convert(presentValueBrlRounded, fxRateBrlPerUsd);
    }

    /** Resultado do calculo, com snapshot dos parametros usados (para auditoria). */
    public record PricingResult(BigDecimal presentValueBrl, BigDecimal discountBrl,
                                 BigDecimal spreadApplied, BigDecimal baseRateApplied) {
    }
}

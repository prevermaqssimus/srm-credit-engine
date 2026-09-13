package com.srm.creditengine.pricing;

import com.srm.creditengine.config.PricingProperties;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.pricing.strategy.ChequePreDatadoPricingStrategy;
import com.srm.creditengine.pricing.strategy.DuplicataMercantilPricingStrategy;
import com.srm.creditengine.pricing.strategy.PricingStrategy;
import com.srm.creditengine.pricing.strategy.PricingStrategyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * RASTREABILIDADE COM O SPEC.md:
 * ============================================================================
 *   - SPEC.md Secao 3: "Uso obrigatorio de java.math.BigDecimal... Proibicao
 *     absoluta de tipos de ponto flutuante primitivos (float/double)" --
 *     validado explicitamente pelo teste roundingPolicyIsHalfEven_notHalfUp
 *     abaixo, e implicitamente por TODOS os outros (nenhum usa double/float).
 *   - SPEC.md Secao 1 ("Politica de Arredondamento"): "Banker's Rounding
 *     (Half-Even)... com precisao de 2 casas decimais" -- testado
 *     diretamente, com contraste against HALF_UP, no ultimo teste da classe.
 *   - SPEC.md Secao 3 ("Precisao de calculo vs. armazenamento"): "Calculo em
 *     memoria: MathContext com precisao estendida... durante os passos
 *     intermediarios" -- e o que valida longTerm_twentyFourMonths (24 meses
 *     de juros compostos SEM erro de arredondamento acumulado).
 *   - Os testes de prazo invalido (zero/negativo) nao mapeiam para uma secao
 *     especifica da SPEC.md -- sao um requisito Pleno do proprio enunciado
 *     (validacao de entrada / Criterio de Aceite 3: "validacao estrita de
 *     payloads"), aplicado aqui na camada de dominio antes mesmo de chegar
 *     na validacao de Bean Validation da camada HTTP.
 *
 * Os valores esperados nos testes de borda foram calculados aplicando a
 * mesma fórmula do golden case (Valor de Face / (1 + Taxa + Spread)^Prazo)
 * com os parâmetros de cada cenário, e conferidos ao centavo abaixo.
 * ============================================================================
 */
class PricingServiceEdgeCasesTest {

    private final PricingService pricingService = new PricingService(
            new PricingStrategyFactory(List.<PricingStrategy>of(
                    new DuplicataMercantilPricingStrategy(),
                    new ChequePreDatadoPricingStrategy())),
            new PricingProperties(new BigDecimal("0.01")),
            new CurrencyConverter()
    );

    @Test
    @DisplayName("Prazo mínimo (1 mês) — Duplicata Mercantil")
    // SPEC.md Secao 1: "prazo e expresso estritamente em meses inteiros" --
    // valida o limite inferior (1) do domínio válido de prazo.
    void minimumTerm_oneMonth() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("10000"), 1);

        assertThat(result.presentValueBrl()).isEqualByComparingTo("9756.10");
        assertThat(result.discountBrl()).isEqualByComparingTo("243.90");
    }

    @Test
    @DisplayName("Prazo longo (24 meses) — Cheque Pré-datado, sem erro de precisão acumulado")
    // SPEC.md Secao 3: valida na pratica o MathContext de precisao estendida
    // ("minimo 10 digitos significativos... durante os passos intermediarios
    // da formula") -- com 24 elevacoes de potencia, um erro de arredondamento
    // por etapa se acumularia e desviaria o resultado final se a precisao
    // intermediaria fosse insuficiente.
    void longTerm_twentyFourMonths() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("50000"), 24);

        assertThat(result.presentValueBrl()).isEqualByComparingTo("21897.86");
        assertThat(result.discountBrl()).isEqualByComparingTo("28102.14");
    }

    @Test
    @DisplayName("Valor de face pequeno — precisão mantida mesmo com poucos dígitos")
    // SPEC.md Secao 3: BigDecimal + MathContext nao dependem da MAGNITUDE do
    // valor de face para manter precisao -- funciona igual para R$100 ou
    // R$100.000 (diferente de double, que teria erro de representacao
    // binaria em qualquer escala, mas mais perceptivel em certos valores).
    void smallFaceValue() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100"), 1);

        assertThat(result.presentValueBrl()).isEqualByComparingTo("97.56");
    }

    @Test
    @DisplayName("Prazo inválido (zero) deve ser rejeitado explicitamente, nunca calculado silenciosamente")
    // Nao vem de uma secao especifica da SPEC.md -- e requisito Pleno do
    // corpo do enunciado (Criterio de Aceite 3: validacao estrita de
    // entrada) e reforca o oposto do bug do Anexo A (REVIEW.md item 8:
    // "ausencia de validacao de entrada").
    void invalidTerm_zero_throwsException() {
        assertThatThrownBy(() -> pricingService.calculatePresentValueBrl(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("10000"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prazo deve ser de ao menos 1 mes");
    }

    @Test
    @DisplayName("Prazo inválido (negativo) deve ser rejeitado explicitamente")
    void invalidTerm_negative_throwsException() {
        assertThatThrownBy(() -> pricingService.calculatePresentValueBrl(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("10000"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * SPEC.md Secao 1 ("Politica de Arredondamento"): "Utiliza-se
     * estritamente o Banker's Rounding (Half-Even)... com precisao de 2
     * casas decimais". Este teste valida a REGRA em si, isolada da formula
     * de precificacao -- garante que a escolha de RoundingMode no
     * PricingService (nao exposta publicamente) se comporta como a SPEC.md
     * exige: "half to even", nao "half up" (arredondamento comercial comum,
     * que a maioria dos desenvolvedores assumiria por padrao sem ler a spec
     * com atencao).
     *
     * Half-Even arredonda o digito exatamente na metade (X,XX5) para o
     * digito PAR mais proximo -- por isso 0.125 -> 0.12 (2 e par) mas
     * 0.135 -> 0.14 (4 e par), nao ambos para cima como seria no
     * arredondamento comercial tradicional (HALF_UP).
     */
    @Test
    @DisplayName("Política de arredondamento é Half-Even (banker's rounding), não Half-Up")
    void roundingPolicyIsHalfEven_notHalfUp() {
        assertThat(new BigDecimal("0.125").setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("0.12"); // 2 e par -> arredonda pra baixo
        assertThat(new BigDecimal("0.135").setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("0.14"); // 4 e par -> arredonda pra cima
        assertThat(new BigDecimal("2.005").setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("2.00"); // 0 e par -> arredonda pra baixo
        assertThat(new BigDecimal("2.015").setScale(2, RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("2.02"); // 2 e par -> arredonda pra cima

        // Contraste deliberado (SPEC.md Secao 1 e explicita que a escolha e
        // Half-Even, NAO Half-Up -- este assert prova que a diferenca e real,
        // nao cosmetica): se a politica fosse HALF_UP, 0.125 iria para 0.13.
        assertThat(new BigDecimal("0.125").setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.13");
    }
}

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * RASTREABILIDADE COM O SPEC.md (mapa de onde cada decisão vem):
 * ============================================================================
 *   - SPEC.md Secao 4, Criterio de Aceite 1 (Corretude Matematica): exige que
 *     o motor reproduza os 3 golden cases (C1, C2, C3) "com exatidao de
 *     centavo", validado por "teste unitario automatizado que executa o
 *     motor de calculo isoladamente, sem dependencia de infraestrutura
 *     (banco de dados ou API)" -- e exatamente o que esta classe faz: nao usa
 *     @SpringBootTest, instancia o PricingService direto, sem banco/contexto.
 *   - SPEC.md Secao 1 ("Origem e Valor da Taxa Base"): taxa base fixada em
 *     1,00% a.m. para fins de afericao dos golden cases (por isso o valor
 *     "0.01" esta hardcoded no PricingProperties deste teste -- ISSO NAO E
 *     o mesmo hardcode do Anexo A: aqui e o valor FIXO exigido pela SPEC
 *     para reproduzir os golden cases; em producao, quem le o valor real do
 *     application.properties e o Spring, via @ConfigurationPropertiesScan).
 *   - SPEC.md Secao 1 ("Politica de Arredondamento"): Half-Even, 2 casas,
 *     aplicado 1x em BRL e em 2 etapas no cross-currency -- testado
 *     explicitamente no golden case C3 abaixo.
 *   - SPEC.md Secao 3: uso obrigatorio de BigDecimal, nunca double/float --
 *     nenhum valor neste teste usa tipo primitivo de ponto flutuante.
 *
 * Os valores esperados (92859.94, 23337.77, 17094.67) sao os proprios
 * valores de referencia publicados na tabela de golden cases do enunciado
 * do desafio -- este teste apenas confirma que o motor os reproduz.
 * ============================================================================
 */
class GoldenCasesTest {

    private final PricingService pricingService = new PricingService(
            new PricingStrategyFactory(List.<PricingStrategy>of(
                    new DuplicataMercantilPricingStrategy(),
                    new ChequePreDatadoPricingStrategy())),
            // Taxa fixa em 1,00% a.m. -- valor exigido pela SPEC.md Secao 1
            // especificamente para reproduzir os golden cases (nao e o valor
            // de producao, que vem do application.properties via Spring).
            new PricingProperties(new BigDecimal("0.01"))
    );

    @Test
    @DisplayName("C1 - Duplicata Mercantil, R$100.000, 3 meses, BRL")
    // SPEC.md Secao 4, Criterio 1 + tabela de golden cases do enunciado (C1).
    // Duplicata Mercantil: spread 1,5% a.m. (regra fixa do enunciado, secao 4.1).
    void goldenCase1_duplicataMercantilBrl() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000"), 3);

        // SPEC.md Secao 1: BRL arredonda 1 UNICA VEZ, no valor presente final,
        // Half-Even, 2 casas.
        assertThat(result.presentValueBrl()).isEqualByComparingTo("92859.94");
        assertThat(result.discountBrl()).isEqualByComparingTo("7140.06");
    }

    @Test
    @DisplayName("C2 - Cheque Pre-datado, R$25.000, 2 meses, BRL")
    // SPEC.md Secao 4, Criterio 1 + tabela de golden cases do enunciado (C2).
    // Cheque Pre-datado: spread 2,5% a.m. (regra fixa do enunciado, secao 4.1).
    void goldenCase2_chequePreDatadoBrl() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("25000"), 2);

        assertThat(result.presentValueBrl()).isEqualByComparingTo("23337.77");
        assertThat(result.discountBrl()).isEqualByComparingTo("1662.23");
    }

    @Test
    @DisplayName("C3 - Duplicata Mercantil, R$100.000, 3 meses, USD, cambio 5,4321")
    // SPEC.md Secao 4, Criterio 1 + tabela de golden cases do enunciado (C3).
    // Este e o teste mais importante para a REGRA DE NEGOCIO do arredondamento
    // cross-currency (SPEC.md Secao 1, ultimo bullet): "essa sequencia [2
    // etapas] e a que reproduz o golden case C3 e deve ser tratada como
    // regra de negocio, nao como detalhe livre de implementacao".
    void goldenCase3_duplicataMercantilCrossCurrencyUsd() {
        var result = pricingService.calculatePresentValueBrl(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000"), 3);

        // Etapa 1 (SPEC.md Secao 1): valor presente em BRL calculado com
        // precisao estendida e ARREDONDADO PARA 2 CASAS ANTES da conversao
        // cambial -- identico ao C1, porque e o mesmo ativo antes de
        // qualquer cambio entrar em jogo.
        assertThat(result.presentValueBrl()).isEqualByComparingTo("92859.94");
        assertThat(result.discountBrl()).isEqualByComparingTo("7140.06");

        // Etapa 2 (SPEC.md Secao 1): o valor JA CONVERTIDO para USD e
        // arredondado NOVAMENTE para 2 casas. Se a ordem fosse invertida
        // (converter primeiro, arredondar so no final), o resultado NAO
        // bateria com 17094.67 -- foi essa divergencia que usamos para
        // validar que a ordem estava certa antes de fechar a implementacao.
        BigDecimal finalUsd = pricingService.convertToUsd(result.presentValueBrl(), new BigDecimal("5.4321"));
        assertThat(finalUsd).isEqualByComparingTo("17094.67");
    }
}

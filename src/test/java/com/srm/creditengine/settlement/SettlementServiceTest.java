package com.srm.creditengine.settlement;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.domain.enums.SettlementStatus;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.domain.model.Settlement;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Passo 11 do plano: "fluxo completo de liquidação funcional via teste de
 * integração direto no service (@Transactional, ainda sem HTTP)".
 *
 * @SpringBootTest sobe o contexto Spring completo -- diferente do
 * GoldenCasesTest (que instancia tudo manualmente), aqui queremos testar a
 * ORQUESTRAÇÃO de verdade: injeção de dependência real, transação real,
 * banco H2 real por trás do ReceivableRepository/SettlementRepository.
 *
 * @Transactional na classe de teste: cada teste roda dentro de uma
 * transação revertida ao final -- não precisa limpar o banco manualmente
 * entre testes (mesmo princípio do @DataJpaTest usado no Passo 10).
 *
 * IMPORTANTE: o teste de liquidação em USD faz uma chamada HTTP REAL para
 * open.er-api.com (via ApiExchangeRateProvider, Passo 8) -- requer internet
 * disponível ao rodar este teste. Isso é uma limitação conhecida deste
 * passo; testes que isolam essa dependência (com Mockito/WireMock) ficam
 * para uma iteração futura, se necessário.
 */
@SpringBootTest
@Transactional
class SettlementServiceTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ReceivableRepository receivableRepository;

    @Test
    void settle_completesFlow_forBrlSettlement() {
        Receivable receivable = receivableRepository.save(new Receivable(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000"), 3,
                Currency.BRL, "Cedente Teste", "Sacado Teste"));

        Settlement settlement = settlementService.settle(
                receivable.getId(), Currency.BRL, UUID.randomUUID().toString());

        // Mesmos valores do golden case C1 -- confirma que a orquestração
        // não alterou o resultado matemático já validado.
        assertThat(settlement.getPresentValueBrl()).isEqualByComparingTo("92859.94");
        assertThat(settlement.getDiscountBrl()).isEqualByComparingTo("7140.06");
        assertThat(settlement.getFinalAmount()).isEqualByComparingTo("92859.94");
        assertThat(settlement.getFxRateUsed()).isNull(); // liquidação em BRL não usa câmbio

        // Confirma que o Receivable foi atualizado NA MESMA transação.
        Receivable updated = receivableRepository.findById(receivable.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void settle_throwsException_whenReceivableDoesNotExist() {
        assertThatThrownBy(() -> settlementService.settle(999999L, Currency.BRL, UUID.randomUUID().toString()))
                .isInstanceOf(ReceivableNotFoundException.class);
    }

    @Test
    void settle_throwsException_whenReceivableAlreadySettled() {
        Receivable receivable = receivableRepository.save(new Receivable(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("25000"), 2,
                Currency.BRL, "Cedente X", "Sacado Y"));

        // Primeira liquidação: sucesso.
        settlementService.settle(receivable.getId(), Currency.BRL, UUID.randomUUID().toString());

        // Segunda tentativa, CHAVE DE IDEMPOTÊNCIA DIFERENTE: deve ser barrada
        // pela checagem de status (não pela idempotência, que exigiria a
        // MESMA chave -- ver SettlementIdempotencyTest, Passo 12, para esse
        // outro cenário).
        assertThatThrownBy(() -> settlementService.settle(
                receivable.getId(), Currency.BRL, UUID.randomUUID().toString()))
                .isInstanceOf(ReceivableAlreadySettledException.class);
    }
}

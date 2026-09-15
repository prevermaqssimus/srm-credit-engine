package com.srm.creditengine.settlement;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.domain.model.Settlement;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import com.srm.creditengine.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tradução: LiquidacaoIdempotenciaTest (plano original) → SettlementIdempotencyTest.
 *
 * Passo 12 do plano: "Resultado esperado: duas chamadas com a mesma chave
 * geram apenas uma liquidação".
 *
 * CORREÇÃO (registrar em AI_USAGE.md como caso de erro identificado): a
 * versão anterior deste teste usava settlementRepository.findAll() para
 * contar os registros -- isso funciona quando este teste roda ISOLADO, mas
 * falha quando a suíte completa roda em sequência, porque o banco H2 é
 * COMPARTILHADO entre classes de teste na mesma execução do Maven (só o
 * @Transactional de CADA CLASSE reverte o que ELA MESMA criou -- não limpa
 * o que outras classes já commitaram antes). O SettlementConcurrencyTest
 * (Passo 13), em especial, faz commits REAIS e definitivos (não pode usar
 * @Transactional de teste, pois roda em threads separadas) -- esses
 * registros "sobram" no banco quando os testes seguintes rodam.
 *
 * CORREÇÃO APLICADA: filtrar a contagem pelo receivableId deste teste
 * especificamente, em vez de contar TODOS os settlements do banco inteiro.
 */
@SpringBootTest
@Transactional
class SettlementIdempotencyTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ReceivableRepository receivableRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    void duplicateRequestWithSameIdempotencyKey_doesNotCreateSecondSettlement() {
        Receivable receivable = receivableRepository.save(new Receivable(
                ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("25000"), 2,
                Currency.BRL, "Cedente Idempotencia", "Sacado Idempotencia"));

        String idempotencyKey = "chave-fixa-teste-idempotencia";

        Settlement first = settlementService.settle(receivable.getId(), Currency.BRL, idempotencyKey);
        Settlement second = settlementService.settle(receivable.getId(), Currency.BRL, idempotencyKey);

        assertThat(second.getId()).isEqualTo(first.getId());

        // CORRIGIDO: conta só os settlements DESTE receivable específico,
        // não todos os settlements do banco (que pode ter "sobras" de
        // outras classes de teste, como SettlementConcurrencyTest, que
        // fazem commits reais e não são revertidos por @Transactional).
        long countForThisReceivable = settlementRepository.findAll().stream()
                .filter(s -> s.getReceivableId().equals(receivable.getId()))
                .count();
        assertThat(countForThisReceivable).isEqualTo(1);
    }
}
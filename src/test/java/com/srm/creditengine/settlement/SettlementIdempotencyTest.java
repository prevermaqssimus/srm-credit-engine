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
 * IMPORTANTE (nota de correção): uma versão anterior deste arquivo, escrita
 * antes do SettlementService/repositórios existirem, usava o enum
 * inexistente "CurrencyCode" -- corrigido aqui para "Currency" (o enum real,
 * criado no Passo 1). Essa versão anterior tinha sido guardada fora de
 * src/ até este ponto do plano, quando as classes das quais ela depende
 * (SettlementService, ReceivableRepository, SettlementRepository) passaram
 * a existir de fato.
 *
 * SPEC.md Secao 4, Criterio de Aceite 2 (Idempotencia): "Requisições
 * duplicadas (por retry de rede ou duplo clique) com a mesma chave de
 * idempotência não podem gerar novas liquidações".
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

        // Primeira chamada: processa de verdade.
        Settlement first = settlementService.settle(receivable.getId(), Currency.BRL, idempotencyKey);

        // Segunda chamada, MESMA chave: deve ser um "replay", retornando o
        // settlement já existente -- simula retry de rede ou duplo clique
        // (o cenário exato do Anexo B do enunciado).
        Settlement second = settlementService.settle(receivable.getId(), Currency.BRL, idempotencyKey);

        assertThat(second.getId()).isEqualTo(first.getId());

        // A prova definitiva: só existe 1 registro no banco para essa chave,
        // nunca 2 -- é essa contagem que falharia se a checagem de
        // idempotência não existisse (o bug do Anexo A).
        assertThat(settlementRepository.findAll()).hasSize(1);
    }
}

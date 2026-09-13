package com.srm.creditengine.settlement;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tradução: LiquidacaoConcorrenciaTest (plano original) → SettlementConcurrencyTest.
 *
 * Passo 13 do plano: "Ajuste: Recebivel ganha @Version" (já feito no Passo 4)
 * + "Resultado esperado: conflito de concorrência tratado corretamente".
 *
 * DIFERENÇA em relação ao SettlementIdempotencyTest (Passo 12) -- não
 * confundir os dois:
 *   - Idempotência (Passo 12) = MESMA requisição repetida (mesma chave),
 *     em SEQUÊNCIA -- resolvida pela checagem em código.
 *   - Concorrência (este teste, Passo 13) = requisições DIFERENTES (chaves
 *     de idempotência diferentes, geradas com UUID abaixo), tentando
 *     liquidar o MESMO recebível NO MESMO INSTANTE -- resolvida pelo
 *     Optimistic Locking (@Version), não pela checagem de idempotência.
 *
 * SPEC.md Secao 4, Criterio de Aceite 3: "proteção contra concorrência
 * (ex.: Optimistic Locking via @Version nas entidades)".
 *
 * Nota de correção: uma versão anterior deste arquivo usava o enum
 * inexistente "CurrencyCode" -- corrigido para "Currency" aqui, mesma
 * correção já aplicada no SettlementIdempotencyTest (Passo 12).
 */
@SpringBootTest
class SettlementConcurrencyTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ReceivableRepository receivableRepository;

    @Test
    void twoConcurrentSettlementsOfSameReceivable_onlyOneSucceeds() throws InterruptedException {
        Receivable receivable = receivableRepository.save(new Receivable(
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000"), 3,
                Currency.BRL, "Cedente Teste", "Sacado Teste"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        // CountDownLatch duplo: garante que as duas threads comecem a
        // liquidacao NO MESMO INSTANTE -- sem isso, o teste poderia
        // "passar" por sorte (uma thread termina antes da outra sequer
        // comecar), sem provar concorrência real.
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Chaves de idempotência DIFERENTES (UUID aleatório cada) -- de
        // propósito, para isolar o efeito do Optimistic Locking do efeito
        // da checagem de idempotência (testada separadamente no Passo 12).
        Callable<Void> attemptSettlement = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                settlementService.settle(receivable.getId(), Currency.BRL, UUID.randomUUID().toString());
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(attemptSettlement);
        Future<Void> f2 = executor.submit(attemptSettlement);

        readyLatch.await();
        startLatch.countDown(); // libera as duas threads simultaneamente

        try {
            f1.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof OptimisticLockingFailureException)) throw new RuntimeException(e);
            conflictCount.incrementAndGet();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        try {
            f2.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof OptimisticLockingFailureException)) throw new RuntimeException(e);
            conflictCount.incrementAndGet();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        executor.shutdown();

        // A prova central: de duas tentativas concorrentes, EXATAMENTE uma
        // deve suceder e EXATAMENTE uma deve falhar por conflito de versão
        // -- nunca 2 sucessos (dupla liquidação) nem 0 sucessos (trava
        // indevida do sistema).
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }
}

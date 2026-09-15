package com.srm.creditengine.settlement.repository;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.model.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Tradução: LiquidacaoRepository (plano original) → SettlementRepository.
 *
 * Passo 10 do plano: repositório básico via Spring Data JPA.
 *
 * findByIdempotencyKey: necessário desde o início da checagem de
 * idempotência (Passo 12).
 *
 * findExtrato: requisito 4.1.6 do desafio -- "rota analítica com filtro
 * por período, cedente e moeda" + paginação server-side (Critério de
 * Aceite 6 do SPEC.md). Cada parâmetro é opcional (null = sem filtro
 * naquele campo) -- é por isso que a query usa "OR :param IS NULL" em vez
 * de vários métodos derivados combinando todas as combinações possíveis
 * de filtros.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);

    /**
     * NOTA (bug corrigido): a versão original desta query usava
     * ":param IS NULL OR coluna = :param" sem CAST -- funciona perfeitamente
     * em H2, mas quebra em Postgres com "could not determine data type of
     * parameter $N". Motivo: no trecho ":param IS NULL", o parâmetro nunca é
     * comparado a nada com tipo conhecido -- H2 infere um tipo por conta
     * própria nesse caso, mas o driver JDBC do Postgres exige que o tipo
     * seja determinável SEM olhar pro valor (fase de "Parse" do protocolo
     * estendido, antes de qualquer bind). O CAST(:param AS tipo) resolve
     * isso ao dar essa informação explicitamente.
     */
    @Query("""
            SELECT s FROM Settlement s
            WHERE (CAST(:cedente AS string) IS NULL OR s.cedente = :cedente)
              AND (CAST(:currency AS string) IS NULL OR s.settlementCurrency = :currency)
              AND (CAST(:startDate AS timestamp) IS NULL OR s.settledAt >= :startDate)
              AND (CAST(:endDate AS timestamp) IS NULL OR s.settledAt <= :endDate)
            """)
    Page<Settlement> findExtrato(@Param("cedente") String cedente,
                                 @Param("currency") Currency currency,
                                 @Param("startDate") Instant startDate,
                                 @Param("endDate") Instant endDate,
                                 Pageable pageable);
}
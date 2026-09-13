package com.srm.creditengine.domain.enums;

/**
 * Tradução: StatusLiquidacao (plano original, em português) →
 * SettlementStatus (nomenclatura adotada no projeto, em inglês).
 *
 * Status de liquidação de um recebível. Vive no próprio Receivable
 * (domain/model/Receivable) — indica se aquele ativo já foi liquidado ou
 * ainda está pendente de liquidação.
 *
 * Atenção para não confundir dois conceitos parecidos:
 *   - SettlementStatus (este enum): um CAMPO dentro de Receivable, que só
 *     tem 2 valores possíveis e muda de estado (PENDING → SETTLED).
 *   - Settlement (classe em domain/model/): o REGISTRO da liquidação em si,
 *     que nunca muda de estado depois de criado — é imutável, para fins de
 *     auditoria (ver REVIEW.md e DECISIONS.md sobre auditabilidade).
 *
 * Passo 1 do plano de construção: enum sem dependências externas.
 */
public enum SettlementStatus {

    /** Recebível ainda não foi liquidado — pode ser precificado e liquidado. */
    PENDING,

    /** Recebível já foi liquidado — uma nova tentativa de liquidação deve
     *  ser rejeitada (ver ReceivableAlreadySettledException). */
    SETTLED
}

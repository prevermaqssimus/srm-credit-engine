package com.srm.creditengine.domain.enums;

/**
 * Tradução: TipoRecebivel (plano original, em português) → ReceivableType
 * (nomenclatura adotada no projeto, em inglês — ver decisão de convenção
 * registrada em AI_USAGE.md: código em inglês, documentação de negócio em
 * português).
 *
 * Tipo do recebível. Cada tipo tem seu próprio spread de risco, definido
 * NÃO aqui, mas na PricingStrategy correspondente (pacote pricing/strategy/)
 * — este enum é só o identificador de qual strategy resolver (Strategy
 * Pattern), para evitar duas fontes de verdade sobre o valor do spread.
 *
 * Passo 1 do plano de construção: enum sem dependências externas, deve
 * apenas compilar isoladamente (sem import de nenhuma outra classe do
 * projeto).
 */
public enum ReceivableType {

    /**
     * Duplicata Mercantil — termo de negócio do mercado financeiro
     * brasileiro, sem tradução literal para inglês; mantido como está no
     * código, igual fizemos com termos de negócio no REVIEW.md e SPEC.md.
     * Spread aplicado: 1,5% a.m. (ver DuplicataMercantilPricingStrategy).
     */
    DUPLICATA_MERCANTIL,

    /**
     * Cheque Pré-datado — idem acima: termo de negócio brasileiro, mantido
     * sem tradução.
     * Spread aplicado: 2,5% a.m. (ver ChequePreDatadoPricingStrategy).
     */
    CHEQUE_PRE_DATADO
}

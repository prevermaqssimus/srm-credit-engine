package com.srm.creditengine.domain.enums;

/**
 * Tradução: Moeda (plano original, em português) → Currency (nomenclatura
 * adotada no projeto, em inglês).
 *
 * Moedas suportadas pelo fundo — caixa multimoedas, conforme o contexto de
 * negócio da SRM Asset (contexto de globalização do portfólio, ver
 * SPEC.md).
 *
 * Passo 1 do plano de construção: enum sem dependências externas.
 *
 * Evolutivo: adicionar uma nova moeda aqui exige também cadastrar o par de
 * câmbio correspondente no Currency Engine (FxRate) — este enum sozinho não
 * basta para o sistema operar com ela.
 */
public enum Currency {

    /** Real brasileiro — moeda nativa dos títulos/recebíveis do fundo. */
    BRL,

    /** Dólar americano — moeda de liquidação para operações cross-currency. */
    USD
}

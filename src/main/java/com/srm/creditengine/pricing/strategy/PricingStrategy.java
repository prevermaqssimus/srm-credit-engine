package com.srm.creditengine.pricing.strategy;

import com.srm.creditengine.domain.enums.ReceivableType;

import java.math.BigDecimal;

/**
 * Tradução: EstrategiaPrecificacao (plano original) → PricingStrategy.
 *
 * Strategy do motor de precificacao -- cada tipo de recebivel implementa
 * esta interface para prover seu proprio spread de risco.
 *
 * DECISAO DE DESIGN (diferenca do plano original, registrar em DECISIONS.md):
 * o plano original desenhava o metodo como
 *   BigDecimal calcularValorPresente(Recebivel recebivel)
 * -- ou seja, a Strategy receberia a ENTIDADE inteira. Optamos por uma
 * assinatura mais enxuta, onde a Strategy so devolve o SPREAD (um valor),
 * e quem orquestra o calculo com os dados primitivos (valor de face, prazo)
 * e o PricingService. Motivo: reduz o acoplamento entre a camada de
 * precificacao e o modelo de persistencia -- a formula nao precisa saber
 * nada sobre como o recebivel e armazenado no banco (nenhum import de
 * domain.model aqui), so precisa do TIPO (enum) e do spread correspondente.
 * Isso tambem e o que permite este Passo 5 ser implementado ANTES do
 * Passo 4 (entidades), sem dependencia circular ou fora de ordem.
 */
public interface PricingStrategy {

    ReceivableType getSupportedType();

    /** Spread de risco mensal deste tipo de recebivel, ex: 0.015 para 1,5% a.m. */
    BigDecimal getSpread();
}

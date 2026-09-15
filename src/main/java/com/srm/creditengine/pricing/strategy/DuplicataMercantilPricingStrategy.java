package com.srm.creditengine.pricing.strategy;

import com.srm.creditengine.domain.enums.ReceivableType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Spread fixo de 1,5% a.m., conforme definido no enunciado (secao 4.1).
 * @Component: o Spring injeta esta classe automaticamente na lista que o
 * PricingStrategyFactory recebe via construtor -- nao ha Factory manual
 * com if/switch (correcao aplicada pelo plano, ver PricingStrategyFactory).
 */
@Component
public class DuplicataMercantilPricingStrategy implements PricingStrategy {

    private static final BigDecimal SPREAD = new BigDecimal("0.015");

    @Override
    public ReceivableType getSupportedType() {
        return ReceivableType.DUPLICATA_MERCANTIL;
    }

    @Override
    public BigDecimal getSpread() {
        return SPREAD;
    }
}

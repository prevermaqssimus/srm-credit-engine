package com.srm.creditengine.pricing.strategy;

import com.srm.creditengine.domain.enums.ReceivableType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Spread fixo de 2,5% a.m., conforme definido no enunciado (secao 4.1). */
@Component
public class ChequePreDatadoPricingStrategy implements PricingStrategy {

    private static final BigDecimal SPREAD = new BigDecimal("0.025");

    @Override
    public ReceivableType getSupportedType() {
        return ReceivableType.CHEQUE_PRE_DATADO;
    }

    @Override
    public BigDecimal getSpread() {
        return SPREAD;
    }
}

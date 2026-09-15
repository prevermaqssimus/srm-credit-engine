package com.srm.creditengine.dto;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Contrato de entrada para a SIMULAÇÃO de valor líquido -- não persiste
 * nada, é usada pelo painel do operador para dar feedback em tempo real
 * enquanto o recebível ainda está sendo digitado (requisito 4.2.1 e
 * Critério de Aceite 5 do SPEC.md: resposta em até 300ms, sem quebrar o
 * fluxo de digitação).
 *
 * Mesmos campos de CreateReceivableRequest, exceto cedente/sacado --
 * irrelevantes para o cálculo de precificação em si.
 */
public record SimulateReceivableRequest(
        @NotNull(message = "Tipo do recebível é obrigatório")
        ReceivableType type,

        @NotNull(message = "Valor de face é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor de face deve ser positivo")
        BigDecimal faceValue,

        @NotNull(message = "Prazo é obrigatório")
        @Min(value = 1, message = "Prazo deve ser de ao menos 1 mês")
        Integer termMonths,

        @NotNull(message = "Moeda de pagamento é obrigatória")
        Currency paymentCurrency
) {
}

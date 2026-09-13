package com.srm.creditengine.dto;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Contrato de entrada da API para cadastro de recebível -- desacoplado da
 * entidade JPA Receivable (nunca expor a entidade direto pro cliente).
 *
 * Bean Validation (SPEC.md Critério de Aceite 3: "validação estrita de
 * payloads") -- se algum campo violar essas anotações, o Spring rejeita a
 * requisição ANTES do controller ser chamado, com HTTP 400 e mensagem
 * detalhada (ver GlobalExceptionHandler).
 */
public record CreateReceivableRequest(
        @NotNull(message = "Tipo do recebível é obrigatório")
        ReceivableType type,

        @NotNull(message = "Valor de face é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor de face deve ser positivo")
        BigDecimal faceValue,

        @NotNull(message = "Prazo é obrigatório")
        @Min(value = 1, message = "Prazo deve ser de ao menos 1 mês")
        Integer termMonths,

        @NotNull(message = "Moeda de pagamento é obrigatória")
        Currency paymentCurrency,

        @NotBlank(message = "Cedente é obrigatório")
        String cedente,

        @NotBlank(message = "Sacado é obrigatório")
        String sacado
) {
}

package com.srm.creditengine.dto;

import com.srm.creditengine.domain.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Contrato de entrada da API para liquidação de um recebível.
 *
 * idempotencyKey: gerada pelo CLIENTE (não pelo servidor) -- ex: um UUID
 * gerado no momento em que o operador clica "Confirmar" no painel. Se a
 * requisição precisar ser reenviada (retry de rede, duplo clique), o
 * cliente reenvia a MESMA chave, e o SettlementService (Passo 11-12)
 * garante que isso não gera uma segunda liquidação.
 */
public record SettleRequest(
        @NotNull(message = "ID do recebível é obrigatório")
        Long receivableId,

        @NotNull(message = "Moeda de liquidação é obrigatória")
        Currency currency,

        @NotBlank(message = "Chave de idempotência é obrigatória")
        String idempotencyKey
) {
}

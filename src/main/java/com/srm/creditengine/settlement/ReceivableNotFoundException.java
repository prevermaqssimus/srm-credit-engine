package com.srm.creditengine.settlement;

/**
 * Lançada quando o SettlementService recebe um receivableId que não existe
 * no banco. Ainda não está conectada a um GlobalExceptionHandler (Passo 14)
 * -- por enquanto se propaga como RuntimeException comum.
 */
public class ReceivableNotFoundException extends RuntimeException {
    public ReceivableNotFoundException(Long receivableId) {
        super("Recebível não encontrado: " + receivableId);
    }
}

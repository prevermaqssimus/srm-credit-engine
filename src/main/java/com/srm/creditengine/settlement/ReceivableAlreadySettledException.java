package com.srm.creditengine.settlement;

/**
 * Lançada quando se tenta liquidar um recebível cujo status já é SETTLED.
 * Checagem básica de estado -- a proteção robusta contra CONCORRÊNCIA real
 * (duas requisições simultâneas passando por essa checagem ao mesmo tempo)
 * só vem no Passo 13 (Optimistic Locking).
 */
public class ReceivableAlreadySettledException extends RuntimeException {
    public ReceivableAlreadySettledException(Long receivableId) {
        super("Recebível " + receivableId + " já foi liquidado.");
    }
}

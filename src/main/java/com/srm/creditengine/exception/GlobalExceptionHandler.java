package com.srm.creditengine.exception;

import com.srm.creditengine.currency.provider.ExchangeRateUnavailableException;
import com.srm.creditengine.settlement.ReceivableAlreadySettledException;
import com.srm.creditengine.settlement.ReceivableNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Passo 14 do plano: tratamento global de exceções. Princípio inegociável
 * (REVIEW.md item 2, e seção 12 do enunciado): NENHUMA exceção é engolida
 * silenciosamente, NENHUM erro retorna 200 OK -- é literalmente o oposto
 * do bug do Anexo A.
 *
 * Cada handler mapeia uma exceção de domínio para um status HTTP semântico,
 * com corpo estruturado (timestamp, status, mensagem) -- nunca uma stack
 * trace crua vazando pro cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ReceivableNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ReceivableNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ReceivableAlreadySettledException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadySettled(ReceivableAlreadySettledException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleFxUnavailable(ExchangeRateUnavailableException ex) {
        // 422: requisição válida, mas não pode ser processada com segurança
        // agora (SPEC.md Seção 5: nunca liquidar com taxa desatualizada).
        log.warn("fx_rate_unavailable_rejected {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("optimistic_lock_conflict {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT,
                "O recebível foi modificado concorrentemente por outra operação. Tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Cobre, entre outros casos, a constraint UNIQUE de idempotency_key
        // sendo violada por uma corrida raríssima entre a checagem em
        // código e o save (defesa em profundidade -- ver Passo 12).
        log.warn("data_integrity_violation {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflito de integridade de dados na operação.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, "Payload inválido: " + details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // Último recurso: loga com stacktrace completo (erro não mapeado
        // precisa ser investigado) e responde 500 -- nunca 200 disfarçado.
        log.error("unexpected_error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

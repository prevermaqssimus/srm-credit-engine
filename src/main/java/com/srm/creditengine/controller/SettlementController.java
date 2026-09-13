package com.srm.creditengine.controller;

import com.srm.creditengine.domain.model.Settlement;
import com.srm.creditengine.dto.SettleRequest;
import com.srm.creditengine.settlement.SettlementService;
import com.srm.creditengine.settlement.repository.SettlementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Camada HTTP para liquidação -- este é o endpoint que finalmente conecta,
 * via HTTP, tudo que foi construído nos passos anteriores (precificação,
 * câmbio, idempotência, optimistic locking).
 */
@RestController
@RequestMapping("/api/settlements")
@Tag(name = "Liquidação", description = "Liquidação de recebíveis com deságio, idempotente e auditável")
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementRepository settlementRepository;

    public SettlementController(SettlementService settlementService, SettlementRepository settlementRepository) {
        this.settlementService = settlementService;
        this.settlementRepository = settlementRepository;
    }

    @PostMapping
    @Operation(summary = "Liquida um recebível",
            description = "Calcula o valor presente (com deságio) e registra a liquidação de forma imutável. " +
                    "Idempotente: reenviar a mesma idempotencyKey nunca gera uma segunda liquidação.")
    @ApiResponse(responseCode = "201", description = "Liquidação realizada com sucesso")
    @ApiResponse(responseCode = "404", description = "Recebível não encontrado")
    @ApiResponse(responseCode = "409", description = "Recebível já liquidado, ou conflito de concorrência")
    @ApiResponse(responseCode = "422", description = "Taxa de câmbio indisponível (cross-currency)")
    public ResponseEntity<Settlement> settle(@Valid @RequestBody SettleRequest request) {
        Settlement settlement = settlementService.settle(
                request.receivableId(), request.currency(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/settlements/" + settlement.getId()))
                .body(settlement);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma liquidação pelo ID")
    public ResponseEntity<Settlement> getById(@PathVariable Long id) {
        return settlementRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

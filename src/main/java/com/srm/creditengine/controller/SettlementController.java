package com.srm.creditengine.controller;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.model.Settlement;
import com.srm.creditengine.dto.SettleRequest;
import com.srm.creditengine.settlement.SettlementService;
import com.srm.creditengine.settlement.repository.SettlementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

/**
 * Camada HTTP para liquidação -- este é o endpoint que finalmente conecta,
 * via HTTP, tudo que foi construído nos passos anteriores (precificação,
 * câmbio, idempotência, optimistic locking).
 */
@RestController
@RequestMapping("/api/settlements")
@Tag(name = "Liquidações", description = "Liquidação de recebíveis com deságio, idempotente e auditável")
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

    @GetMapping
    @Tag(name = "Extrato Analítico", description = "Consulta de liquidações com filtros e paginação server-side")
    @Operation(summary = "Extrato analítico de liquidações, com filtros e paginação server-side",
            description = "Requisito 4.1.6: filtro por período (startDate/endDate), cedente e moeda -- " +
                    "todos opcionais e combináveis. Paginação sempre server-side (Critério de Aceite 6 do " +
                    "SPEC.md): a resposta nunca traz mais registros do que o 'size' pedido, mesmo que o " +
                    "total de liquidações cresça.")
    @ApiResponse(responseCode = "200", description = "Página de liquidações retornada com sucesso")
    public Page<Settlement> extrato(
            @Parameter(description = "Filtra pelo nome exato do cedente (opcional)")
            @RequestParam(required = false) String cedente,

            @Parameter(description = "Filtra pela moeda da liquidação: BRL ou USD (opcional)")
            @RequestParam(required = false) Currency currency,

            @Parameter(description = "Data/hora inicial (inclusive), formato ISO-8601 (opcional)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,

            @Parameter(description = "Data/hora final (inclusive), formato ISO-8601 (opcional)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,

            @Parameter(description = "Número da página, começando em 0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Tamanho da página")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "settledAt"));
        return settlementRepository.findExtrato(cedente, currency, startDate, endDate, pageable);
    }
}
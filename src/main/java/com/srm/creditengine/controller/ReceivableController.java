package com.srm.creditengine.controller;

import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.dto.CreateReceivableRequest;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import com.srm.creditengine.settlement.ReceivableNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Camada HTTP para recebíveis -- recebe requisição, valida payload (Bean
 * Validation via @Valid), delega ao repositório, devolve status code
 * semântico. Nunca calcula nem decide regra de negócio aqui (isso é
 * responsabilidade de PricingService/SettlementService).
 */
@RestController
@RequestMapping("/api/receivables")
@Tag(name = "Recebíveis", description = "Cadastro e consulta de recebíveis (duplicatas, cheques pré-datados)")
public class ReceivableController {

    private final ReceivableRepository receivableRepository;

    public ReceivableController(ReceivableRepository receivableRepository) {
        this.receivableRepository = receivableRepository;
    }

    @PostMapping
    @Operation(summary = "Cadastra um novo recebível",
            description = "Cria um recebível com status PENDING, pronto para simulação e liquidação futura.")
    @ApiResponse(responseCode = "201", description = "Recebível criado com sucesso")
    @ApiResponse(responseCode = "400", description = "Payload inválido (ver detalhes na resposta)")
    public ResponseEntity<Receivable> create(@Valid @RequestBody CreateReceivableRequest request) {
        Receivable receivable = new Receivable(
                request.type(), request.faceValue(), request.termMonths(),
                request.paymentCurrency(), request.cedente(), request.sacado());
        Receivable saved = receivableRepository.save(receivable);
        return ResponseEntity.created(URI.create("/api/receivables/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um recebível pelo ID")
    @ApiResponse(responseCode = "200", description = "Recebível encontrado")
    @ApiResponse(responseCode = "404", description = "Recebível não encontrado")
    public Receivable getById(@PathVariable Long id) {
        return receivableRepository.findById(id)
                .orElseThrow(() -> new ReceivableNotFoundException(id));
    }

    @GetMapping
    @Operation(summary = "Lista todos os recebíveis cadastrados")
    public Iterable<Receivable> listAll() {
        return receivableRepository.findAll();
    }
}

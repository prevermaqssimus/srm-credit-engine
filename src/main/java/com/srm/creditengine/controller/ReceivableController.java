package com.srm.creditengine.controller;

import com.srm.creditengine.currency.provider.ExchangeRateProvider;
import com.srm.creditengine.currency.provider.ExchangeRateResult;
import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.dto.CreateReceivableRequest;
import com.srm.creditengine.dto.SimulateReceivableRequest;
import com.srm.creditengine.dto.SimulationResult;
import com.srm.creditengine.pricing.PricingService;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import com.srm.creditengine.settlement.ReceivableNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Camada HTTP para recebíveis -- recebe requisição, valida payload (Bean
 * Validation via @Valid), delega ao repositório, devolve status code
 * semântico. Nunca calcula nem decide regra de negócio aqui (isso é
 * responsabilidade de PricingService/SettlementService).
 *
 * O endpoint /simulate é a exceção proposital: ele CHAMA PricingService
 * diretamente (em vez de delegar a outro service), porque não existe
 * orquestração nenhuma aqui -- é literalmente o mesmo cálculo que
 * SettlementService faz, só que sem tocar em Receivable/Settlement no
 * banco. Ver SimulationResult para o porquê disso nunca ser persistido.
 */
@RestController
@RequestMapping("/api/receivables")
@Tag(name = "Recebíveis", description = "Cadastro, consulta e simulação de recebíveis (duplicatas, cheques pré-datados)")
public class ReceivableController {

    private final ReceivableRepository receivableRepository;
    private final PricingService pricingService;
    private final ExchangeRateProvider exchangeRateProvider;

    public ReceivableController(ReceivableRepository receivableRepository,
                                PricingService pricingService,
                                ExchangeRateProvider exchangeRateProvider) {
        this.receivableRepository = receivableRepository;
        this.pricingService = pricingService;
        this.exchangeRateProvider = exchangeRateProvider;
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

    @PostMapping("/simulate")
    @Tag(name = "Simulação", description = "Preview de valor líquido, sem persistir nada")
    @Operation(summary = "Simula o valor líquido de um recebível, sem persistir nada",
            description = "Usado pelo painel do operador para dar feedback em tempo real enquanto o " +
                    "recebível está sendo preenchido (SPEC.md, Critério de Aceite 5: resposta em até 300ms " +
                    "para BRL). Para USD, consulta a taxa de câmbio vigente NESTE instante -- é uma prévia, " +
                    "não uma cotação garantida até a liquidação de fato ser confirmada.")
    @ApiResponse(responseCode = "200", description = "Simulação calculada com sucesso")
    @ApiResponse(responseCode = "400", description = "Payload inválido (ver detalhes na resposta)")
    @ApiResponse(responseCode = "422", description = "Taxa de câmbio indisponível (simulação em USD)")
    public SimulationResult simulate(@Valid @RequestBody SimulateReceivableRequest request) {
        PricingService.PricingResult pricing = pricingService.calculatePresentValueBrl(
                request.type(), request.faceValue(), request.termMonths());

        BigDecimal finalAmount;
        BigDecimal fxRateUsed = null;

        if (request.paymentCurrency() == Currency.USD) {
            ExchangeRateResult rate = exchangeRateProvider.getCurrentRate(Currency.USD, Currency.BRL);
            fxRateUsed = rate.rate();
            finalAmount = pricingService.convertToUsd(pricing.presentValueBrl(), fxRateUsed);
        } else {
            finalAmount = pricing.presentValueBrl();
        }

        return new SimulationResult(
                pricing.presentValueBrl(), pricing.discountBrl(), finalAmount,
                request.paymentCurrency(), fxRateUsed);
    }
}
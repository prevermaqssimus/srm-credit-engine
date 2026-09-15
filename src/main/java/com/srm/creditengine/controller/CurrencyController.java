package com.srm.creditengine.controller;

import com.srm.creditengine.currency.provider.ExchangeRateProvider;
import com.srm.creditengine.currency.provider.ExchangeRateResult;
import com.srm.creditengine.domain.enums.Currency;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * Camada HTTP do Currency Engine -- requisito 4.1.1 do desafio: "armazenar
 * e prover taxas... com endpoint de atualização manual OU integração
 * mockada. Cada taxa deve ter data/hora de vigência".
 *
 * Este projeto optou pela segunda opção (integração real, não mockada,
 * com open.er-api.com) -- ver ApiExchangeRateProvider. Deliberadamente
 * NÃO existe endpoint de escrita manual de taxa (POST/PUT): SPEC.md,
 * Seção 2, item 6, documenta essa decisão -- permitir inserção manual
 * abriria risco de manipulação de preço (uma taxa poderia ser cadastrada
 * artificialmente, sem verificação contra o mercado real).
 *
 * O que este controller PROVÊ é a consulta (GET) da taxa vigente, com seu
 * timestamp de obtenção ("data/hora de vigência") e qual provedor
 * respondeu -- exatamente os dados que SettlementService já usa
 * internamente ao liquidar, agora expostos para consulta direta (útil
 * para o operador conferir a taxa antes de confirmar uma liquidação, ou
 * para qualquer integração externa que precise saber a taxa do momento
 * sem precisar liquidar nada).
 */
@RestController
@RequestMapping("/api/exchange-rates")
@Tag(name = "Currency Engine", description = "Consulta de taxas de câmbio vigentes (sem escrita manual -- ver SPEC.md Seção 2.6)")
public class CurrencyController {

    private final ExchangeRateProvider exchangeRateProvider;

    public CurrencyController(ExchangeRateProvider exchangeRateProvider) {
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @GetMapping("/latest")
    @Operation(summary = "Consulta a taxa de câmbio vigente entre duas moedas",
            description = "Retorna a taxa obtida NESTE instante do provedor configurado, junto com o " +
                    "timestamp de vigência e o nome do provedor que respondeu. Não há cache/armazenamento " +
                    "de histórico de taxas -- cada chamada consulta o provedor em tempo real (mesma " +
                    "política usada na liquidação real, SPEC.md Seção 5).")
    @ApiResponse(responseCode = "200", description = "Taxa obtida com sucesso")
    @ApiResponse(responseCode = "422", description = "Provedor de câmbio indisponível no momento")
    public ExchangeRateResult latest(
            @RequestParam(defaultValue = "USD") Currency from,
            @RequestParam(defaultValue = "BRL") Currency to) {
        return exchangeRateProvider.getCurrentRate(from, to);
    }
}

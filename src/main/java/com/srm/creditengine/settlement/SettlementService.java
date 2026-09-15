package com.srm.creditengine.settlement;

import com.srm.creditengine.currency.provider.ExchangeRateProvider;
import com.srm.creditengine.currency.provider.ExchangeRateResult;
import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.SettlementStatus;
import com.srm.creditengine.domain.model.Receivable;
import com.srm.creditengine.domain.model.Settlement;
import com.srm.creditengine.pricing.PricingService;
import com.srm.creditengine.receivable.repository.ReceivableRepository;
import com.srm.creditengine.settlement.repository.SettlementRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Tradução: LiquidacaoService (plano original) → SettlementService.
 *
 * Passo 11 do plano: orquestrador da liquidação. Responsabilidade única
 * (SOLID "S"): ORQUESTRAR -- nunca calcular nada diretamente. Toda a
 * matemática já mora em PricingService/CurrencyConverter; toda a consulta
 * de câmbio já mora em ExchangeRateProvider. Este service só decide A
 * ORDEM em que essas peças são chamadas, e garante atomicidade (@Transactional).
 *
 * Nota sobre o construtor: o plano original lista
 * "MotorPrecificacao, ProvedorTaxaCambio, LiquidacaoRepository, Clock".
 * Adicionamos também ReceivableRepository -- necessário para buscar o
 * recebível a liquidar e persistir a mudança de status, e não estava
 * listado explicitamente no plano, mas é indispensável para o fluxo
 * funcionar (sem ele, não haveria como carregar o Receivable do banco).
 *
 * Escopo deste passo (não confundir com passos futuros):
 *   - Idempotência ROBUSTA (constraint UNIQUE + tratamento de violação) é
 *     o Passo 12 -- aqui só existe uma checagem básica de existência.
 *   - Optimistic Locking sob CONCORRÊNCIA REAL (duas threads simultâneas)
 *     é o Passo 13 -- aqui a checagem de status já-liquidado é sequencial,
 *     não testada sob concorrência ainda.
 *
 * Observabilidade (requisito Sênior do desafio-tecnico, secao 6):
 * "settlements.completed" conta liquidações NOVAS de fato concluídas --
 * deliberadamente NÃO incrementado no caminho de replay de idempotência
 * (linha ~65 abaixo), já que ali nenhuma liquidação nova aconteceu, só
 * foi devolvido um registro já existente. Tag "currency" permite separar
 * BRL de USD no /actuator/metrics, sem precisar de duas métricas
 * distintas. Junto com "pricing.calculation.duration" (PricingService),
 * são as 2 métricas de negócio exigidas pelo desafio -- ambas via
 * Micrometer, independentes dos logs estruturados (logback-spring.xml).
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PricingService pricingService;
    private final ExchangeRateProvider exchangeRateProvider;
    private final SettlementRepository settlementRepository;
    private final ReceivableRepository receivableRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    // Nota (transparência): 'clock' é injetado mas ainda não é usado no corpo
    // deste método -- Receivable/Settlement geram seus timestamps
    // internamente com Instant.now() direto (decisão já tomada nos Passos 4/6:
    // não retrofitar as entidades já commitadas). O campo fica aqui pronto
    // para uso caso este service precise gerar algum timestamp próprio no
    // futuro (ex: log de auditoria adicional).

    public SettlementService(PricingService pricingService,
                             ExchangeRateProvider exchangeRateProvider,
                             SettlementRepository settlementRepository,
                             ReceivableRepository receivableRepository,
                             Clock clock,
                             MeterRegistry meterRegistry) {
        this.pricingService = pricingService;
        this.exchangeRateProvider = exchangeRateProvider;
        this.settlementRepository = settlementRepository;
        this.receivableRepository = receivableRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Settlement settle(Long receivableId, Currency settlementCurrency, String idempotencyKey) {

        // Checagem básica de idempotência (versão robusta = Passo 12).
        Settlement existing = settlementRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            log.info("settlement_idempotent_replay idempotency_key={} settlement_id={}",
                    idempotencyKey, existing.getId());
            return existing;
        }

        Receivable receivable = receivableRepository.findById(receivableId)
                .orElseThrow(() -> new ReceivableNotFoundException(receivableId));

        if (receivable.getStatus() == SettlementStatus.SETTLED) {
            throw new ReceivableAlreadySettledException(receivableId);
        }

        // 1. Precificação em BRL (moeda nativa do título) -- PricingService.
        PricingService.PricingResult pricing = pricingService.calculatePresentValueBrl(
                receivable.getType(), receivable.getFaceValue(), receivable.getTermMonths());

        BigDecimal finalAmount;
        BigDecimal fxRateUsed = null;
        String providerUsed = null;

        // 2. Se cross-currency, consulta câmbio real (ExchangeRateProvider)
        //    e delega a conversão ao PricingService/CurrencyConverter.
        if (settlementCurrency == Currency.USD) {
            ExchangeRateResult rate = exchangeRateProvider.getCurrentRate(Currency.USD, Currency.BRL);
            fxRateUsed = rate.rate();
            providerUsed = rate.providerName();
            finalAmount = pricingService.convertToUsd(pricing.presentValueBrl(), fxRateUsed);
        } else {
            finalAmount = pricing.presentValueBrl();
        }

        // 3. Persistência do registro imutável.
        Settlement settlement = new Settlement(
                receivable.getId(),
                receivable.getCedente(),
                receivable.getFaceValue(),
                pricing.presentValueBrl(),
                pricing.discountBrl(),
                finalAmount,
                settlementCurrency,
                fxRateUsed,
                providerUsed,
                pricing.spreadApplied(),
                pricing.baseRateApplied(),
                idempotencyKey
        );
        Settlement saved = settlementRepository.save(settlement);

        // 4. Atualização de status do recebível, na MESMA transação (ACID).
        receivable.markAsSettled();
        receivableRepository.save(receivable);

        // Métrica de negócio: conta só liquidações NOVAS (não replays de
        // idempotência, que retornam antes de chegar aqui). Tag "currency"
        // permite ver BRL/USD separadamente em /actuator/metrics.
        meterRegistry.counter("settlements.completed", "currency", settlementCurrency.name()).increment();

        log.info("settlement_completed settlement_id={} receivable_id={} currency={} amount={} fx_rate={}",
                saved.getId(), receivableId, settlementCurrency, finalAmount, fxRateUsed);

        return saved;
    }
}
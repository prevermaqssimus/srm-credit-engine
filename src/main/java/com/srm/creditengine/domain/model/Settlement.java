package com.srm.creditengine.domain.model;

import com.srm.creditengine.domain.enums.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tradução: Liquidacao (plano original) → Settlement.
 *
 * Registro IMUTÁVEL de uma liquidação. Sem setters de propósito: uma
 * liquidação registrada nunca é alterada (requisito de auditabilidade,
 * SPEC.md Critério 4). Qualquer correção futura é um NOVO registro
 * (estorno), nunca um UPDATE neste.
 *
 * Passo 4 do plano: só estrutura, zero lógica de cálculo.
 *
 * idempotencyKey tem constraint UNIQUE — é essa constraint no banco,
 * não só a checagem em código, que garante idempotência sob concorrência
 * real (ver SettlementService, Passo 11-12).
 */
@Entity
@Table(name = "settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_idempotency_key", columnNames = "idempotency_key")
})
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receivable_id", nullable = false)
    private Long receivableId;

    @Column(name = "face_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal faceValue;

    @Column(name = "present_value_brl", nullable = false, precision = 19, scale = 2)
    private BigDecimal presentValueBrl;

    @Column(name = "discount_brl", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountBrl;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_currency", nullable = false, length = 3)
    private Currency settlementCurrency;

    /** Taxa de câmbio usada, null se liquidação foi em BRL (moeda nativa). */
    @Column(name = "fx_rate_used", precision = 19, scale = 6)
    private BigDecimal fxRateUsed;

    @Column(name = "spread_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal spreadApplied;

    @Column(name = "base_rate_applied", nullable = false, precision = 10, scale = 6)
    private BigDecimal baseRateApplied;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    protected Settlement() {
        // JPA
    }

    public Settlement(Long receivableId, BigDecimal faceValue, BigDecimal presentValueBrl,
                       BigDecimal discountBrl, BigDecimal finalAmount, Currency settlementCurrency,
                       BigDecimal fxRateUsed, BigDecimal spreadApplied, BigDecimal baseRateApplied,
                       String idempotencyKey) {
        this.receivableId = receivableId;
        this.faceValue = faceValue;
        this.presentValueBrl = presentValueBrl;
        this.discountBrl = discountBrl;
        this.finalAmount = finalAmount;
        this.settlementCurrency = settlementCurrency;
        this.fxRateUsed = fxRateUsed;
        this.spreadApplied = spreadApplied;
        this.baseRateApplied = baseRateApplied;
        this.idempotencyKey = idempotencyKey;
        this.settledAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getReceivableId() { return receivableId; }
    public BigDecimal getFaceValue() { return faceValue; }
    public BigDecimal getPresentValueBrl() { return presentValueBrl; }
    public BigDecimal getDiscountBrl() { return discountBrl; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public Currency getSettlementCurrency() { return settlementCurrency; }
    public BigDecimal getFxRateUsed() { return fxRateUsed; }
    public BigDecimal getSpreadApplied() { return spreadApplied; }
    public BigDecimal getBaseRateApplied() { return baseRateApplied; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getSettledAt() { return settledAt; }
}

package com.srm.creditengine.domain.model;

import com.srm.creditengine.domain.enums.Currency;
import com.srm.creditengine.domain.enums.ReceivableType;
import com.srm.creditengine.domain.enums.SettlementStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tradução: Recebivel (plano original) → Receivable.
 *
 * Recebível adquirido do cedente (duplicata, cheque pré-datado, etc).
 * Passo 4 do plano: só estrutura de dados JPA, ZERO lógica de cálculo
 * (SOLID "S" — Single Responsibility).
 *
 * Simplificação de design em relação ao plano original: o plano previa
 * `Cedente` e `Sacado` como entidades JPA separadas. Optamos por campos
 * String simples aqui — não há necessidade demonstrada, no escopo deste
 * case, de cedente/sacado terem atributos próprios (histórico, rating,
 * etc.) que justifiquem uma tabela dedicada. Registrar em DECISIONS.md se
 * isso for questionado na defesa.
 *
 * @Version implementa Optimistic Locking (Passo 13 do plano) — protege
 * contra duas liquidações concorrentes do mesmo recebível.
 */
@Entity
@Table(name = "receivables")
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivableType type;

    @NotNull
    @DecimalMin(value = "0.01", message = "Valor de face deve ser positivo")
    @Column(name = "face_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal faceValue;

    @NotNull
    @Min(value = 1, message = "Prazo deve ser de ao menos 1 mes")
    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_currency", nullable = false, length = 3)
    private Currency paymentCurrency;

    @NotBlank
    @Column(nullable = false)
    private String cedente;

    @NotBlank
    @Column(nullable = false)
    private String sacado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    protected Receivable() {
        // JPA
    }

    public Receivable(ReceivableType type, BigDecimal faceValue, Integer termMonths,
                       Currency paymentCurrency, String cedente, String sacado) {
        this.type = type;
        this.faceValue = faceValue;
        this.termMonths = termMonths;
        this.paymentCurrency = paymentCurrency;
        this.cedente = cedente;
        this.sacado = sacado;
        this.status = SettlementStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public ReceivableType getType() { return type; }
    public BigDecimal getFaceValue() { return faceValue; }
    public Integer getTermMonths() { return termMonths; }
    public Currency getPaymentCurrency() { return paymentCurrency; }
    public String getCedente() { return cedente; }
    public String getSacado() { return sacado; }
    public SettlementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }

    public void markAsSettled() {
        this.status = SettlementStatus.SETTLED;
    }
}

package com.srm.creditengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Tradução: PrecificacaoProperties (plano original) → PricingProperties.
 *
 * Configuração externalizada da taxa base do motor de precificação — lida
 * de application.properties (prefixo "pricing"), NUNCA hardcoded no código.
 *
 * Isso resolve diretamente o item 7 do REVIEW.md: o Anexo A tinha
 * `const BASE_RATE = 1.0;` fixo no código-fonte do endpoint de liquidação,
 * sem vínculo com vigência e exigindo novo deploy para qualquer alteração.
 * Aqui, a taxa é injetável via construtor em qualquer classe que precisar
 * dela (ver PricingService), e pode ser alterada só editando
 * application.properties, sem recompilar.
 *
 * Usa record (imutável) com constructor binding automático do Spring Boot
 * 3.x — não precisa de setters nem de @Component: basta habilitar o scan
 * com @ConfigurationPropertiesScan na classe principal da aplicação
 * (ver CreditEngineApplication).
 *
 * Property esperada em application.properties:
 *   pricing.base-rate=0.01
 * (kebab-case "base-rate" é automaticamente ligado ao campo "baseRate"
 * do record — relaxed binding padrão do Spring Boot.)
 *
 * Passo 2 do plano de construção.
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(BigDecimal baseRate) {
}

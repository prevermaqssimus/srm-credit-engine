package com.srm.creditengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Passo 6 do plano de construção: abstração de tempo para testabilidade
 * determinística.
 *
 * Problema que isso resolve: qualquer classe que chamar Instant.now() ou
 * LocalDateTime.now() diretamente é difícil de testar de forma
 * determinística -- o valor muda a cada execução do teste, tornando
 * impossível fazer um assertThat(timestamp).isEqualTo(valorFixo).
 *
 * Solução: nenhuma classe de negócio chama Instant.now() diretamente.
 * Todas recebem um Clock injetado via construtor, e usam Instant.now(clock).
 * Em produção, o Spring injeta o Bean abaixo (relógio real, UTC). Em teste,
 * pode-se sobrescrever esse Bean com Clock.fixed(...) para ter um instante
 * fixo e previsível, permitindo assertThat(timestamp).isEqualTo(instanteFixo).
 *
 * Por que UTC e não o fuso local: timestamps de auditoria/liquidação em um
 * sistema financeiro devem ser armazenados em UTC, sem ambiguidade de fuso
 * horário -- a conversão para o fuso de exibição (ex: horário de Brasília)
 * é responsabilidade da camada de apresentação (frontend/relatório), nunca
 * do dado persistido.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

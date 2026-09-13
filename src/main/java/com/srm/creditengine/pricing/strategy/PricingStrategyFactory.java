package com.srm.creditengine.pricing.strategy;

import com.srm.creditengine.domain.enums.ReceivableType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tradução: MotorPrecificacao (parcialmente -- a parte de "montar o mapa" do
 * plano original) → PricingStrategyFactory.
 *
 * Resolve a PricingStrategy correta a partir do ReceivableType. O Spring
 * injeta automaticamente TODAS as implementacoes de PricingStrategy
 * encontradas no contexto (List<PricingStrategy> no construtor) -- nao ha
 * Factory manual com if/switch. Adicionar um novo tipo de recebivel na
 * "mudanca ao vivo" da defesa = criar uma nova classe @Component
 * implementando PricingStrategy -- zero linha alterada aqui.
 */
@Component
public class PricingStrategyFactory {

    private final Map<ReceivableType, PricingStrategy> strategiesByType;

    public PricingStrategyFactory(List<PricingStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toMap(PricingStrategy::getSupportedType, Function.identity()));
    }

    public PricingStrategy resolve(ReceivableType type) {
        PricingStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Nenhuma strategy de precificacao registrada para o tipo: " + type);
        }
        return strategy;
    }
}

package com.srm.creditengine.settlement.repository;

import com.srm.creditengine.domain.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Tradução: LiquidacaoRepository (plano original) → SettlementRepository.
 *
 * Passo 10 do plano: repositório básico via Spring Data JPA.
 *
 * Único método customizado adicionado aqui (além do CRUD padrão): a busca
 * por idempotencyKey. Diferente de ReceivableRepository (que ficou 100%
 * vazio), este método já é necessário e previsível desde já -- não é
 * especulação: a checagem de idempotência (Passo 12) inevitavelmente vai
 * precisar buscar um Settlement pela chave, então declarar o método agora
 * é adiantamento de algo comprovadamente necessário, não excesso de
 * engenharia.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);
}

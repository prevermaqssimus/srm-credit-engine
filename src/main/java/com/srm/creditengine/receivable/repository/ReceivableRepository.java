package com.srm.creditengine.receivable.repository;

import com.srm.creditengine.domain.model.Receivable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tradução: RecebivelRepository (plano original) → ReceivableRepository.
 *
 * Passo 10 do plano: repositório básico via Spring Data JPA -- CRUD pronto
 * automaticamente (save, findById, findAll, delete, etc.) sem precisar
 * escrever nenhuma implementação.
 *
 * Vazio de propósito por enquanto: nenhuma query customizada é necessária
 * até este ponto do plano. Métodos derivados (ex: findByStatus) entram
 * quando algum passo futuro precisar deles -- adicionar antecipadamente
 * seria especular sobre necessidade ainda não comprovada.
 */
public interface ReceivableRepository extends JpaRepository<Receivable, Long> {
}

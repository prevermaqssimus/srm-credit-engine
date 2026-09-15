# ADR 0001 — Monólito Modular em vez de Microsserviços

## Status
Aceito

## Contexto
O desafio técnico pede uma plataforma de cessão de crédito com um domínio de negócio
único e coeso: precificação e liquidação de recebíveis (duplicatas, cheques
pré-datados), com suporte a multimoedas (BRL/USD). Era preciso decidir, desde o
início, entre construir a aplicação como um monólito ou dividir suas
responsabilidades em serviços independentes (microsserviços) desde já.

## Decisão
Optamos por um **monólito modular**: um único processo Spring Boot (Java 21),
organizado internamente em pacotes por domínio (`pricing`, `receivable`,
`settlement`, `currency`, `config`), mas com um único deploy, um único banco de
dados e um único ciclo de build/release.

## Consequências

**Positivas:**
- Menos complexidade operacional — não há orquestração de rede entre serviços,
  nem necessidade de infraestrutura de service discovery, gateway interno, etc.
- Deploy único simplifica testes de integração e depuração — todo o fluxo de
  liquidação é auditável dentro de um único processo/transação.
- Mais simples de entregar corretamente dentro do prazo do desafio técnico.
- Ainda escala horizontalmente se necessário: múltiplas réplicas do mesmo
  monólito podem rodar atrás de um load balancer ou API Gateway (ex: Azure API
  Management), sem exigir reestruturação do código.

**Negativas / trade-offs aceitos:**
- Não permite escalar ou implantar partes do sistema de forma independente
  (ex: escalar só `pricing` sem escalar `settlement`).
- Não permite times diferentes evoluírem módulos de forma totalmente isolada,
  com ciclos de deploy próprios.
- Se o domínio de negócio crescer para múltiplos contextos genuinamente
  desacoplados (ex: um módulo de crédito completamente separado de um módulo de
  gestão de carteira), essa decisão precisaria ser revisitada.

## Alternativas consideradas

**Microsserviços** — descartado. O domínio é único e coeso; dividir em serviços
separados adicionaria complexidade de rede, consistência eventual e operação
(múltiplos deploys, múltiplos bancos, comunicação entre serviços) sem nenhum
ganho real para o escopo deste desafio. Seria over-engineering.

**Arquitetura hexagonal (ports & adapters) formal** — não adotada como estrutura
explícita. A separação por domínio já dá organização suficiente para o tamanho do
projeto; a camada de persistência já é abstraída pelo Spring Data JPA (troca H2
por Postgres só via configuração), o que reduz o ganho marginal de uma hexagonal
explícita frente à complexidade adicional.

**Arquitetura reativa (Spring WebFlux)** — não adotada. O volume esperado para
este motor de crédito não justifica programação não-bloqueante; o modelo
bloqueante tradicional (Spring MVC + JPA) é mais simples de entender, testar e
depurar, com desempenho suficiente para o escopo do desafio.

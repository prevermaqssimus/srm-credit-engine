# ADR 0002 — PostgreSQL como Banco Definitivo, H2 para Desenvolvimento e Testes

## Status
Aceito e validado — Postgres confirmado funcionando de ponta a ponta via Docker
Compose, através dos 11 cenários da coleção Postman (ver `API_TESTING.md`).

## Contexto
O motor de precificação exige persistência relacional com garantias ACID — nenhuma
liquidação pode ficar "pela metade" (SPEC.md, seção 4.3). Era preciso decidir qual
banco de dados usar durante o desenvolvimento e qual seria o banco definitivo do
ambiente de produção, além de como alternar entre os dois sem duplicar esforço de
configuração.

## Decisão
Usar **H2 em memória** durante desenvolvimento e testes automatizados (rápido,
sem dependência de infraestrutura externa, dados descartados a cada reinício), e
**PostgreSQL** como banco definitivo do ambiente de produção, orquestrado via
Docker Compose junto com a aplicação.

A aplicação é agnóstica de banco por design (JPA/Hibernate) — trocar de H2 para
Postgres é uma mudança de configuração (`application.properties`), não de código.

## Consequências

**Positivas:**
- Ciclo de desenvolvimento rápido: testes locais não dependem de Postgres/Docker
  rodando, o que agiliza o dia a dia (rodar direto pelo IntelliJ, por exemplo).
- Todos os testes automatizados (unitários e de integração via `@SpringBootTest`)
  já rodam e passam contra H2, dando confiança na lógica de negócio (golden cases,
  idempotência, concorrência) sem depender de infraestrutura externa.
- Postgres real atende ao requisito explícito do desafio: "Docker + Docker Compose
  orquestrando aplicação e banco" (nível Pleno, cumulativo pro Sênior) — **validado
  na prática**: os 11 cenários da coleção Postman (cadastro, busca, listagem,
  liquidação em BRL/USD com câmbio real, idempotência, e os erros 400/404/409)
  passaram rodando a aplicação via `docker compose up` contra um Postgres real.

**Negativas / trade-offs aceitos:**
- H2 e Postgres podem, em teoria, se comportar diferente em casos de borda (tipos
  de coluna, comportamento de locking) — a validação via Postman cobriu os fluxos
  funcionais principais, mas não testes de concorrência/locking específicos do
  dialeto Postgres.
- Como a aplicação usa uma única fonte de configuração com apenas um banco ativo
  por vez (comentando/descomentando blocos), alternar entre H2 e Postgres é uma
  ação manual, não automática por perfil.

## Alternativas consideradas

**Perfis Spring (`#---` multi-documento, `spring.config.activate.on-profile`)** —
testado e descartado nesta fase. Permitiria alternar H2/Postgres via variável de
ambiente sem editar o arquivo manualmente, mas durante a implementação avaliamos
que o ganho de conveniência não compensava a complexidade adicional de depuração
neste momento do projeto; a abordagem de comentar/descomentar um único bloco de
configuração foi preferida por sua simplicidade e transparência.

**Arquivos de properties separados por perfil** (`application-dev.properties` +
`application-postgres.properties`) — avaliada e adiada. Resolveria a alternância
manual de forma mais elegante, mas o custo de reestruturar a configuração não
compensava frente ao benefício organizacional neste momento do projeto.

**Banco NoSQL (ex: MongoDB)** — descartado. O domínio é fortemente relacional
(recebíveis, liquidações, integridade referencial) e exige transações ACID —
características centrais de bancos relacionais, não o ponto forte de soluções
NoSQL.
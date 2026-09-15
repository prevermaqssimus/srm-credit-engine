# DIAGRAMS.md — SRM Credit Engine

Diagramas exigidos pelo desafio técnico: ER básico (nível Júnior, seção 6) e C4
níveis 1 e 2 (nível Sênior, seção 6). Renderizados via Mermaid — visíveis
diretamente no GitHub, sem ferramenta externa.

---

## Diagrama ER (Entidade-Relacionamento)

```mermaid
erDiagram
    RECEIVABLE ||--o| SETTLEMENT : "liquidado em (0 ou 1)"

    RECEIVABLE {
        Long id PK
        ReceivableType type "DUPLICATA_MERCANTIL | CHEQUE_PRE_DATADO"
        BigDecimal faceValue "NUMERIC(19,4)"
        Integer termMonths
        Currency paymentCurrency "BRL | USD"
        String cedente
        String sacado
        SettlementStatus status "PENDING | SETTLED"
        Instant createdAt
        Long version "optimistic locking"
    }

    SETTLEMENT {
        Long id PK
        Long receivableId FK
        String cedente "snapshot no momento da liquidacao"
        BigDecimal faceValue "NUMERIC(19,4)"
        BigDecimal presentValueBrl "NUMERIC(19,2)"
        BigDecimal discountBrl "NUMERIC(19,2)"
        BigDecimal finalAmount "NUMERIC(19,2)"
        Currency settlementCurrency "BRL | USD"
        BigDecimal fxRateUsed "NUMERIC(19,6), nulo se BRL"
        String providerUsed "nulo se BRL"
        BigDecimal spreadApplied "NUMERIC(10,6), snapshot"
        BigDecimal baseRateApplied "NUMERIC(10,6), snapshot"
        String idempotencyKey UK
        Instant settledAt
    }
```

**Notas de design:**

- **`RECEIVABLE ||--o|  SETTLEMENT`** (um-para-zero-ou-um): um recebível **pode** ter
  no máximo uma liquidação — nunca duas (regra de negócio: `409 Conflict` se já
  `SETTLED`). Um recebível `PENDING` ainda não tem `Settlement` associado.
- **`SETTLEMENT.receivableId`** é uma referência lógica (FK), não um relacionamento
  JPA (`@ManyToOne`) — decisão deliberada: `Settlement` é um registro de auditoria
  **autocontido**, que não depende de navegar até `Receivable` para ter sentido (por
  isso `cedente`, `spreadApplied`, `baseRateApplied` são *snapshots* duplicados, não
  buscados via join).
- **`idempotencyKey`** tem constraint `UNIQUE` no banco — é essa restrição, não só a
  checagem em código, que garante idempotência sob concorrência real.
- **`version`** em `RECEIVABLE` é o campo do Optimistic Locking (`@Version`) — não
  existe em `SETTLEMENT`, porque `Settlement` nunca é atualizado após criado (sem
  setters, imutável).

---

## Diagrama C4 — Nível 1 (Contexto do Sistema)

```mermaid
C4Context
    title SRM Credit Engine — Diagrama de Contexto (C4 Nível 1)

    Person(operador, "Operador da Mesa", "Cadastra recebíveis, simula e confirma liquidações")

    System(creditEngine, "SRM Credit Engine", "Precifica e liquida recebíveis multimoedas com auditabilidade e precisão decimal")

    System_Ext(fxProvider, "open.er-api.com", "Provedor externo de taxas de câmbio em tempo real")
    SystemDb_Ext(database, "PostgreSQL / H2", "Armazena recebíveis e liquidações")

    Rel(operador, creditEngine, "Usa", "HTTPS")
    Rel(creditEngine, fxProvider, "Consulta taxa de câmbio vigente", "HTTPS/REST")
    Rel(creditEngine, database, "Lê e grava dados", "JDBC")
```

---

## Diagrama C4 — Nível 2 (Contêineres)

```mermaid
C4Container
    title SRM Credit Engine — Diagrama de Contêineres (C4 Nível 2)

    Person(operador, "Operador da Mesa", "Cadastra recebíveis, simula e confirma liquidações")

    System_Boundary(creditEngine, "SRM Credit Engine") {
        Container(frontend, "Painel do Operador", "Angular 18", "SPA: cadastro, simulação em tempo real, liquidação, extrato")
        Container(backend, "API REST", "Spring Boot 3 / Java 21", "Motor de precificação, liquidação, Currency Engine, extrato analítico")
        ContainerDb(db, "Banco de Dados", "PostgreSQL 16 (prod) / H2 (dev, testes)", "Recebíveis e liquidações")
    }

    System_Ext(fxProvider, "open.er-api.com", "Provedor externo de câmbio")

    Rel(operador, frontend, "Usa", "HTTPS")
    Rel(frontend, backend, "Chama API REST", "HTTPS/JSON")
    Rel(backend, db, "Lê e grava", "JDBC")
    Rel(backend, fxProvider, "Consulta taxa vigente", "HTTPS/REST, com retry + circuit breaker")
```

**Notas de design:**

- O **Frontend** e o **Backend** são contêineres **separados** (repositórios
  distintos), mas ambos fazem parte do mesmo *System Boundary* lógico — reflete a
  decisão de monólito modular no backend (ver `DECISIONS.md`/ADR 0001), não
  microsserviços: um único contêiner de API, não vários.
- A seta do **Backend → open.er-api.com** já anota "retry + circuit breaker"
  diretamente no diagrama — é a resiliência descrita no `SPEC.md`, Seção 5,
  tornada visível na arquitetura, não só em texto.
- O banco aparece como **um único contêiner lógico** mesmo suportando dois motores
  (Postgres/H2) — a aplicação é agnóstica de banco por design (ver
  `DECISIONS.md`/ADR 0002).

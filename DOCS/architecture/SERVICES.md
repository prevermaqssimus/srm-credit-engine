# SERVICES.md — SRM Credit Engine

Este documento explica **cada serviço** da API, o **endpoint** correspondente e a
**regra de negócio** por trás dele — pensado para consulta rápida (sua ou de quem for
avaliar) sem precisar ler o código-fonte inteiro. Para as premissas e critérios de
aceite formais, ver `SPEC.md`; para decisões de corte/arquitetura, ver `DECISIONS.md`.

---

## 1. Recebíveis (`ReceivableController`)

Cadastro e consulta dos ativos que o fundo adquire (duplicatas, cheques pré-datados).

### `POST /api/receivables` — Cadastrar recebível

**Regra de negócio:** cria um recebível com status inicial `PENDING`. Nenhum cálculo
acontece aqui — é só o registro do ativo, pronto para ser simulado e depois liquidado.

**Validações (Bean Validation):**
- `type`: obrigatório (`DUPLICATA_MERCANTIL` ou `CHEQUE_PRE_DATADO`)
- `faceValue`: obrigatório, mínimo `0.01` (não aceita zero ou negativo)
- `termMonths`: obrigatório, mínimo `1` (não aceita prazo zero)
- `paymentCurrency`: obrigatório (`BRL` ou `USD`)
- `cedente` / `sacado`: obrigatórios, não podem ser vazios

**Resposta:** `201 Created`, com o recebível completo (incluindo `id` gerado,
`status: PENDING`, `createdAt`, `version` para controle de concorrência).

### `GET /api/receivables/{id}` — Buscar por ID

Retorna `404` se o ID não existir (`ReceivableNotFoundException`).

### `GET /api/receivables` — Listar todos

Sem filtro nem paginação — listagem simples (adequado ao nível Júnior do desafio; o
extrato **com** filtro e paginação é responsabilidade da liquidação, seção 6 abaixo).

---

## 2. Motor de Precificação (`PricingService` + Strategy Pattern)

O núcleo matemático do sistema — não é exposto como endpoint próprio, é usado
internamente pela Simulação e pela Liquidação.

**Fórmula (fixa pelo enunciado):**
```
Valor Presente = Valor de Face / (1 + Taxa Base + Spread) ^ Prazo
```

**Regra de negócio — Strategy Pattern (spread por tipo de recebível):**
- `DUPLICATA_MERCANTIL` → spread de **1,5% a.m.**
- `CHEQUE_PRE_DATADO` → spread de **2,5% a.m.**

Cada tipo tem sua própria classe de estratégia (`DuplicataMercantilPricingStrategy`,
`ChequePreDatadoPricingStrategy`), resolvida em runtime pela `PricingStrategyFactory` —
adicionar um novo tipo de recebível no futuro significa criar uma nova estratégia, sem
tocar no `PricingService`.

**Taxa base:** `1,00% a.m.`, vinda de `application.properties`
(`pricing.base-rate=0.01`) — alterável sem recompilar o código.

**Regra de arredondamento (SPEC.md, Seção 1):**
- Cálculo intermediário usa precisão estendida (`MathContext`, 50 dígitos
  significativos) — nunca `float`/`double`.
- Arredondamento final: `HALF_EVEN` (banker's rounding), 2 casas decimais, aplicado
  **uma única vez** sobre o valor presente em BRL.
- Deságio = Valor de Face (arredondado) − Valor Presente (arredondado).

---

## 3. Simulação de Valor Líquido (`ReceivableController.simulate`)

### `POST /api/receivables/simulate` — Simular sem persistir

**Regra de negócio:** dá ao operador uma prévia do resultado **antes** de cadastrar
qualquer coisa. Usa exatamente o mesmo `PricingService` da liquidação real — a
simulação nunca diverge do resultado que a liquidação de fato produziria.

**Como funciona:**
1. Recebe os mesmos dados de um recebível (tipo, valor, prazo, moeda) — **sem**
   cedente/sacado, irrelevantes para o cálculo.
2. Calcula o valor presente em BRL (mesma fórmula da Seção 2).
3. Se a moeda for `USD`, consulta a taxa de câmbio **vigente neste instante** (Seção 4)
   e converte.
4. **Nada é salvo no banco** — nenhum `Receivable` nem `Settlement` é criado.

**Importante:** por consultar câmbio real em tempo real (para USD), o resultado é uma
**prévia**, não uma cotação garantida — a taxa pode mudar entre a simulação e a
liquidação de fato.

**Resposta:** valor presente em BRL, deságio, valor líquido final, moeda, e a taxa de
câmbio usada (`null` se BRL).

---

## 4. Currency Engine (`CurrencyController` + `ExchangeRateProvider`)

Consulta e resiliência de taxas de câmbio.

### `GET /api/exchange-rates/latest?from=USD&to=BRL` — Consultar taxa vigente

**Regra de negócio:** retorna a taxa obtida **neste instante** do provedor real
(`open.er-api.com`), junto com o timestamp de obtenção e o nome do provedor que
respondeu. **Não há cache nem histórico armazenado** — cada chamada consulta o
provedor de novo, de propósito (mesma política usada na liquidação real).

**Por que não existe endpoint de escrita manual de taxa:** decisão deliberada
(`SPEC.md`, Seção 2, item 6) — permitir cadastro manual abriria risco de manipulação de
preço (uma taxa poderia ser inserida artificialmente, sem verificação contra o mercado
real). O Currency Engine só **provê** (lê), nunca aceita escrita.

**Resiliência (Resilience4j), aplicada sempre que o câmbio é consultado (simulação ou
liquidação):**
- **Retry:** até 3 tentativas, com backoff exponencial (500ms → 1s → 2s).
- **Circuit Breaker:** abre se 50% das últimas 10 chamadas falharem; fica "aberto"
  (rejeitando chamadas sem tentar rede) por 30s, depois testa recuperação
  (half-open).
- **Falha total:** a operação (simulação ou liquidação) é rejeitada com erro explícito
  — nunca há fallback silencioso para uma taxa desatualizada.

---

## 5. Liquidação (`SettlementController` + `SettlementService`)

O núcleo transacional do sistema — onde a liquidação de fato acontece e é registrada.

### `POST /api/settlements` — Liquidar um recebível

**Regra de negócio, passo a passo (`SettlementService.settle`):**

1. **Checagem de idempotência** — busca um `Settlement` já existente com a mesma
   `idempotencyKey`. Se existir, **retorna o mesmo registro**, sem criar um novo (nem
   recalcular nada). Constraint `UNIQUE` no banco garante isso mesmo sob concorrência
   real, não só checagem em código.
2. **Busca o recebível** — `404` se o `receivableId` não existir.
3. **Checagem de status** — `409 Conflict` se o recebível já estiver `SETTLED`.
4. **Precificação** — calcula valor presente e deságio em BRL (Seção 2).
5. **Conversão cambial** (se `currency == USD`) — consulta a taxa vigente (Seção 4) e
   converte; o segundo arredondamento (2 casas, `HALF_EVEN`) acontece **depois** da
   conversão, sobre o valor já em USD (regra específica do golden case C3).
6. **Persistência do registro imutável** — cria o `Settlement` com todos os dados de
   auditoria (ver abaixo) e marca o `Receivable` como `SETTLED`, **na mesma
   transação** (`@Transactional` — atomicidade real: ou os dois passos acontecem, ou
   nenhum acontece).

**Concorrência real (Optimistic Locking):** o campo `@Version` no `Receivable`
garante que, se duas requisições tentarem liquidar o mesmo recebível ao mesmo tempo,
apenas uma sucede — a outra recebe erro de conflito de versão, tratado como `409`.

**Auditabilidade — o que fica gravado, para sempre, em cada `Settlement`:**
- Valor de face, valor presente, deságio, valor final
- Moeda da liquidação e taxa de câmbio usada (se cross-currency)
- **Nome do provedor de câmbio que respondeu** (`providerUsed`)
- Spread e taxa base aplicados no momento (snapshot, mesmo que a config mude depois)
- **Cedente** (snapshot do `Receivable` no momento da liquidação — ver Seção 6)
- Chave de idempotência e timestamp de liquidação

`Settlement` **não tem setters** — uma vez criado, nunca é alterado. Qualquer correção
futura seria um novo registro (estorno), nunca um `UPDATE`.

**Erros possíveis:**
- `404` — recebível não existe
- `409` — recebível já liquidado, ou conflito de concorrência (optimistic lock)
- `422` — taxa de câmbio indisponível (circuit breaker aberto ou todas as tentativas
  de retry falharam)

### `GET /api/settlements/{id}` — Buscar liquidação por ID

Retorna `404` se não existir.

---

## 6. Extrato Analítico (`SettlementController.extrato`)

### `GET /api/settlements` — Extrato com filtros e paginação

**Regra de negócio:** lista liquidações já registradas, com filtros **opcionais e
combináveis** entre si, sempre paginado (nunca retorna tudo de uma vez, mesmo que
existam milhares de registros).

**Filtros disponíveis (todos opcionais):**
- `cedente` — nome exato do cedente (usa o snapshot gravado no `Settlement`, não faz
  join com `Receivable` — ver nota abaixo)
- `currency` — `BRL` ou `USD`
- `startDate` / `endDate` — período (formato ISO-8601), inclusive nas duas pontas

**Paginação (sempre ativa, nunca opcional):**
- `page` — número da página, começando em `0` (padrão: `0`)
- `size` — tamanho da página (padrão: `20`)
- Ordenação fixa: mais recentes primeiro (`settledAt` decrescente)

**Nota de design — por que `cedente` está no `Settlement`, não só no `Receivable`:**
o campo é um **snapshot**, capturado no momento exato da liquidação. Isso evita um
`JOIN` a cada consulta do extrato e, mais importante, mantém o registro de liquidação
**autocontido** — se o nome do cedente mudasse no cadastro depois, o extrato
continuaria mostrando o nome de quando a liquidação de fato aconteceu (consistente
com o princípio de auditabilidade da Seção 5).

**Resposta:** objeto de página do Spring Data (`content` com a lista de liquidações
da página atual, mais `totalElements`, `totalPages`, `number` da página atual, etc.).

---

## Resumo — todos os endpoints, em um relance

| Método | Rota | Persiste? | Regra principal |
|---|---|---|---|
| `POST` | `/api/receivables` | Sim | Cadastra recebível, status `PENDING` |
| `GET` | `/api/receivables/{id}` | — | 404 se não existir |
| `GET` | `/api/receivables` | — | Lista simples, sem filtro |
| `POST` | `/api/receivables/simulate` | **Não** | Preview de valor líquido, mesmo motor da liquidação real |
| `GET` | `/api/exchange-rates/latest` | — | Taxa vigente, sempre em tempo real, sem escrita manual |
| `POST` | `/api/settlements` | Sim | Liquida com idempotência, optimistic locking, ACID |
| `GET` | `/api/settlements/{id}` | — | 404 se não existir |
| `GET` | `/api/settlements` | — | Extrato com filtros opcionais + paginação server-side |

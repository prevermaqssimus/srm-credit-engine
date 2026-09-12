# REVIEW.md — Code Review Reverso (Anexo A)

Revisão do endpoint de liquidação (`settlement.controller.ts`), como se fosse um Pull Request submetido para revisão antes do merge. Problemas listados em ordem de severidade, com impacto em produção e correção proposta para cada um. Quando a correção já está formalizada como regra de negócio na `SPEC.md`, o item aponta para a seção correspondente em vez de repetir a solução.

---

## 1. SQL Injection (Crítico)

```typescript
const receivable = await db.queryOne(
    `SELECT * FROM receivables WHERE id = ${receivableId}`
);
```
```typescript
await db.query(
    `INSERT INTO settlements (receivable_id, amount, currency)
   VALUES (${receivableId}, ${finalAmount.toFixed(2)}, '${currency}')`
);
```

**Impacto em produção:** `receivableId` e `currency` vêm diretamente do corpo da requisição e são concatenados na query sem sanitização. Qualquer requisição HTTP manipulada pode injetar SQL arbitrário — exfiltrar dados de outros cedentes ou corromper a base inteira. Gravidade máxima em sistema financeiro.

**Correção proposta:** queries parametrizadas (prepared statements) em toda interação com o banco. Não coberto pela `SPEC.md` — é prática de implementação segura, não regra de negócio; deve ser tratado como padrão de codificação obrigatório (Spring Data JPA/JDBC parametrizado).

---

## 2. Falha silenciosa que quebra ACID e responde sucesso falso (Crítico)

```typescript
try {
    await db.query(`INSERT INTO settlements ...`);
    await db.query(`UPDATE receivables SET status = 'SETTLED' ...`);
} catch (e) {
    // se falhar aqui, o insert já rodou, então segue o jogo
}
res.status(200).json({ ok: true, amount: finalAmount.toFixed(2) });
```

**Impacto em produção:** se o `UPDATE` falhar após o `INSERT`, o erro é ignorado e o endpoint retorna `200 OK` como se tudo tivesse funcionado — o sistema fica em estado inconsistente (settlement existe, recebível não está `SETTLED`) sem sinalização nenhuma. Viola diretamente o anti-padrão citado na seção 12 do enunciado ("erro respondido com 200 OK").

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 4, Critério de Aceite 3** (Resiliência e Segurança) e **Seção 3** (Transacionalidade/ACID via `@Transactional`) — insert e update devem ocorrer na mesma transação, com rollback automático em falha e propagação do erro ao chamador.

---

## 3. Ausência de idempotência (Crítico)

**Impacto em produção:** não existe chave de idempotência nem verificação de duplicidade. Retry de rede ou duplo clique gera duas liquidações completas para o mesmo recebível — é literalmente o incidente descrito no Anexo B.

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 4, Critério de Aceite 2** (Idempotência) — chave de idempotência obrigatória, com constraint de unicidade no banco.

---

## 4. `Math.pow` com `number` (ponto flutuante) em cálculo monetário (Crítico)

```typescript
const BASE_RATE = 1.0;
const presentValue =
  receivable.face_value / Math.pow(1 + BASE_RATE + spread, receivable.term);
```

**Impacto em produção:** `number` é ponto flutuante binário (IEEE 754), o mesmo problema que faz `0.1 + 0.2 !== 0.3`. Aplicado a juros compostos, acumula erro de arredondamento que pode divergir do valor correto em centavos — exatamente o cenário que os golden cases (C1, C2, C3) existem para detectar. É o anti-padrão citado como eliminatório a partir de pleno (seção 12 do enunciado).

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 3** — uso obrigatório de `java.math.BigDecimal` com `RoundingMode.HALF_EVEN`, proibição absoluta de `float`/`double` em qualquer cálculo monetário.

---

## 5. Ausência de transação atômica entre insert e update (Alto)

**Impacto em produção:** mesmo desconsiderando a falha silenciosa do item 2, as duas operações de banco não têm garantia de atomicidade entre si. Um crash da aplicação exatamente entre as duas chamadas deixa o sistema inconsistente de forma permanente, sem erro para investigar.

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 3** (Transacionalidade) — mesma transação cobrindo ambas as operações.

---

## 6. Ausência de controle de concorrência (Alto)

**Impacto em produção:** duas requisições simultâneas e legítimas para o mesmo `receivableId` (dois operadores, ou um retry concorrente) podem ambas ler o recebível ainda não `SETTLED` e ambas prosseguirem — gerando duas liquidações para o mesmo recebível. Diferente da falta de idempotência (item 3): aqui a falha é entre requisições concorrentes distintas, não repetição da mesma requisição.

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 4, Critério de Aceite 3** (*Optimistic Locking* via `@Version`) — a segunda transação que tentar atualizar uma versão já modificada deve falhar com conflito de concorrência.

---

## 7. Taxa base *hardcoded* sem histórico de vigência (Médio)

```typescript
const BASE_RATE = 1.0; // taxa base mensal
```

**Impacto em produção:** taxa fixa no código-fonte, sem vínculo com vigência nem possibilidade de alteração sem novo deploy. Viola o requisito 4.1.1 do enunciado ("cada taxa deve ter data/hora de vigência") e torna auditoria histórica impossível.

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 1** (Origem e Valor da Taxa Base) — taxa gerenciada via configuração externa (`application.properties`), permitindo alteração sem recompilação e registro de qual taxa foi usada em cada liquidação.

---

## 8. Ausência de validação de entrada (Médio)

**Impacto em produção:** não há validação de existência do recebível, de moeda válida, ou de estado já `SETTLED` antes de prosseguir. Um `receivableId` inexistente provavelmente estoura exceção não tratada mais adiante (`receivable.type` em objeto `undefined`), podendo vazar stack trace ao cliente.

**Correção proposta:** já coberta pela `SPEC.md`, **Seção 4, Critério de Aceite 3** (validação estrita via *Bean Validation*) — validação de entrada e verificação de estado do recebível antes de qualquer cálculo, com erros semânticos (`404`, `409`) em vez de exceção não tratada.

---

## Resumo de severidade

| # | Problema | Severidade | Coberto pela SPEC.md |
|---|---|---|---|
| 1 | SQL Injection | Crítico | Não — prática de implementação segura |
| 2 | Falha silenciosa + `200 OK` falso | Crítico | Sim — Seção 4/Critério 3 e Seção 3 |
| 3 | Ausência de idempotência | Crítico | Sim — Seção 4/Critério 2 |
| 4 | `Math.pow`/`number` em cálculo monetário | Crítico | Sim — Seção 3 |
| 5 | Ausência de transação atômica | Alto | Sim — Seção 3 |
| 6 | Ausência de controle de concorrência | Alto | Sim — Seção 4/Critério 3 |
| 7 | Taxa base *hardcoded* sem vigência | Médio | Sim — Seção 1 |
| 8 | Ausência de validação de entrada | Médio | Sim — Seção 4/Critério 3 |

Sete dos oito problemas identificados já são diretamente endereçados pelas regras de negócio definidas na `SPEC.md` — o que confirma que essas decisões foram tomadas justamente para prevenir esta classe de falha. A única exceção (SQL Injection) é uma prática de segurança de implementação, que não é escopo de uma especificação de regras de negócio, mas precisa ser tratada como padrão de codificação obrigatório em qualquer camada de acesso a dados.
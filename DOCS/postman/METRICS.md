# METRICS.md — Observabilidade do SRM Credit Engine

Este documento explica **como** a observabilidade do projeto funciona: o que cada
peça faz, onde vive no código, e como consultar/interpretar os números. Para a
especificação do requisito, ver `SPEC.md`/`desafio-tecnico`, seção 6.

---

## 1. Duas ferramentas diferentes, propósitos diferentes

A observabilidade aqui é composta por **duas peças independentes**, que não
dependem uma da outra para funcionar:

| | Logs estruturados | Métricas de negócio |
|---|---|---|
| **Responde a pergunta** | "O que aconteceu, exatamente, num evento específico?" | "Como o sistema está se comportando, em geral, ao longo do tempo?" |
| **Tecnologia** | Logback + `logstash-logback-encoder` | Micrometer (via Spring Boot Actuator) |
| **Onde configura** | `src/main/resources/logback-spring.xml` | `pom.xml` (dependência) + código Java |
| **Formato de saída** | Uma linha JSON por evento, no console | Números agregados, consultáveis via HTTP |
| **Uso típico** | Investigar um caso específico (ex: por que essa liquidação falhou) | Ver tendência/saúde geral (ex: quantas liquidações por minuto) |

Nenhuma das duas gera a outra. Uma métrica nunca aparece num log; um log nunca vira
métrica automaticamente. São dois caminhos paralelos, ambos exigidos pelo desafio.

---

## 2. Logs estruturados — como funcionam

**Por que existe um arquivo `logback-spring.xml` próprio:** Spring Boot só ganhou
suporte **nativo** a "structured logging" (JSON) na versão 3.4 — este projeto usa
3.3.4, então o formato JSON é configurado manualmente via Logback.

**Comportamento por ambiente:**
- **Local (IntelliJ, sem perfil `postgres` ativo):** logs continuam em texto
  colorido, legível no terminal — nada muda no dia a dia de desenvolvimento.
- **Docker (`SPRING_PROFILES_ACTIVE=postgres`, já configurado no
  `docker-compose.yml`):** logs saem em JSON, uma linha por evento:

```json
{"timestamp":"2026-09-15T20:00:00.000Z","message":"settlement_completed settlement_id=1 receivable_id=1 currency=BRL amount=92859.94 fx_rate=null","logger_name":"com.srm.creditengine.settlement.SettlementService","level":"INFO","application":"srm-credit-engine"}
```

Esse formato é pensado para ser consumido por ferramentas de agregação de log
(ELK, Loki, CloudWatch) sem parsing adicional — cada campo já vem separado.

---

## 3. Métricas de negócio — como funcionam

**Tecnologia-base:** `spring-boot-starter-actuator`, que traz o Micrometer junto.
O Micrometer registra números **em memória**, no próprio processo da aplicação —
nunca precisa escrever em disco, banco, ou log para existir.

**As 2 métricas customizadas exigidas pelo desafio:**

### 3.1. `settlements.completed` — contador (Counter)

**Onde vive:** `SettlementService.settle()`, incrementado logo após a persistência
bem-sucedida (depois do `receivableRepository.save(receivable)`).

```java
meterRegistry.counter("settlements.completed", "currency", settlementCurrency.name())
        .increment();
```

**Regra de negócio importante:** só incrementa em liquidações **novas** — o
caminho de replay de idempotência (quando a mesma `idempotencyKey` é reenviada)
retorna **antes** de chegar nessa linha, então não conta como uma nova liquidação.

**Tag `currency`:** permite consultar BRL e USD separadamente, sem precisar de
duas métricas distintas — o Micrometer trata cada combinação de nome+tag como uma
série própria internamente.

**Para que serve na prática:** é a métrica que permite calcular "liquidações por
minuto" (o exemplo citado no próprio enunciado do desafio) — bastaria observar
como o `COUNT` cresce ao longo do tempo.

### 3.2. `pricing.calculation.duration` — cronômetro (Timer)

**Onde vive:** `PricingService.calculatePresentValueBrl()`, envolvendo o cálculo
inteiro (fórmula do valor presente) via `Timer.record(...)`.

```java
private final Timer calculationTimer;

// no construtor:
this.calculationTimer = Timer.builder("pricing.calculation.duration")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);

// no método:
return calculationTimer.record(() -> {
    // ... fórmula do valor presente ...
});
```

**O que fica deliberadamente FORA da medição:** a validação de `termMonths < 1`
acontece **antes** de entrar no `record(...)` — não faz sentido medir uma
validação trivial junto com o cálculo real.

**Percentis habilitados (p50/p95/p99):** além de média/máximo, o Actuator expõe
percentis — útil para responder "qual a latência típica" (p50) vs "qual a pior
latência que a maioria das chamadas ainda vê" (p95/p99), sem que poucos outliers
distorçam a leitura.

---

## 4. Consultando as métricas — passo a passo

### 4.1. Habilitar a exposição (já configurado)

Por padrão, o Spring Boot só expõe `/actuator/health` publicamente. Para acessar
`/actuator/metrics`, o `application.properties` precisa ter:

```properties
management.endpoints.web.exposure.include=health,metrics
```

### 4.2. Listar todas as métricas disponíveis

```
GET /actuator/metrics
```

Retorna um JSON com o campo `"names"` — contém, entre dezenas de métricas
automáticas da JVM/Tomcat/HikariCP/Resilience4j, as 2 métricas customizadas:
`settlements.completed` e `pricing.calculation.duration`.

> **Nota:** essas 2 são as únicas **escritas manualmente** neste projeto. Todo o
> resto da lista (`jvm.*`, `tomcat.*`, `hikaricp.*`, `resilience4j.*`, etc.) vem
> automaticamente, só por ter o `spring-boot-starter-actuator` no `pom.xml` — sem
> nenhuma linha de código adicional.

### 4.3. Consultar uma métrica específica

```
GET /actuator/metrics/settlements.completed
GET /actuator/metrics/pricing.calculation.duration
```

**Exemplo de resposta real, depois de 6 cálculos:**

```json
{
    "name": "pricing.calculation.duration",
    "baseUnit": "seconds",
    "measurements": [
        { "statistic": "COUNT", "value": 6.0 },
        { "statistic": "TOTAL_TIME", "value": 0.0015158 },
        { "statistic": "MAX", "value": 3.241E-4 }
    ]
}
```

**Como ler:**
- `COUNT: 6.0` → o cálculo rodou 6 vezes desde que a aplicação subiu.
- `TOTAL_TIME: 0.0015158` → soma de todo o tempo gasto nas 6 execuções (≈1,5ms no
  total) — dividindo pelo `COUNT`, a média é ≈0,25ms por cálculo.
- `MAX: 3.241E-4` → notação científica: `3.241 × 10⁻⁴ s` = `0,3241ms` — a execução
  mais lenta registrada até agora.

**Antes de qualquer execução**, a métrica existe, mas vem zerada:
```json
{ "measurements": [
    { "statistic": "COUNT", "value": 0.0 },
    { "statistic": "TOTAL_TIME", "value": 0.0 },
    { "statistic": "MAX", "value": 0.0 }
]}
```
Isso é esperado — a métrica é registrada no boot da aplicação, mas só ganha valor
depois de pelo menos uma chamada real (`POST /api/receivables/simulate` ou
`POST /api/settlements`).

### 4.4. Filtrando por tag

```
GET /actuator/metrics/settlements.completed?tag=currency:BRL
```
Retorna só a contagem de liquidações em BRL, ignorando USD.

---

## 5. Métricas "de graça" que vieram junto (bônus, não exigidas)

Só por ter o `spring-boot-starter-actuator` e o `resilience4j-spring-boot3` no
`pom.xml`, o projeto ganhou dezenas de métricas automáticas, sem escrever nenhum
código extra. As mais relevantes para este domínio:

| Métrica | O que mostra |
|---|---|
| `resilience4j.circuitbreaker.state` | Estado atual do circuit breaker do câmbio (`closed`/`open`/`half_open`) |
| `resilience4j.circuitbreaker.failure.rate` | Taxa de falha nas últimas chamadas ao provedor de câmbio |
| `resilience4j.retry.calls` | Quantas tentativas de retry aconteceram |
| `http.server.requests` | Latência e status code de **cada** endpoint HTTP, automaticamente |
| `hikaricp.connections.active` | Conexões ativas no pool do banco |

Exemplo de consulta ao estado do circuit breaker:
```
GET /actuator/metrics/resilience4j.circuitbreaker.state?tag=state:closed&tag=name:exchangeRateProvider
```
`value: 1.0` significa que o circuit breaker está fechado (saudável) nesse estado.

---

## 6. Testando tudo via Postman

Existe uma coleção Postman **separada** dedicada só a isso:
`docs/postman/srm-credit-engine-observabilidade-postman-collection.json` — ver
`API_TESTING.md` para instruções de importação e ordem de teste.

## Arquitetura

Aplicação Spring Boot (Java 21) organizada em módulos por domínio: `pricing` (motor de
precificação), `receivable` (recebíveis), `settlement` (liquidação), `currency` (provedor
de câmbio externo) e `config` (propriedades externalizadas).

- **Persistência:** JPA/Hibernate — a aplicação é agnóstica de banco por design; funciona
  tanto com **H2** (em memória) quanto com **PostgreSQL**, bastando apontar a configuração
  de datasource correta em `application.properties`.
    - **Resiliência:** Resilience4j (retry com backoff exponencial + circuit breaker) protegendo
      a chamada ao provedor externo de câmbio (`open.er-api.com`).
    - **Taxa de precificação:** externalizada via `application.properties` (`pricing.base-rate`),
      injetada por constructor binding (`PricingProperties`, record imutável) — sem valores
      fixos no código-fonte.



**Estilo arquitetural:** monólito modular — um único processo, organizado por domínio,
não microsserviços, não hexagonal (ports/adapters) e não reativo (WebFlux). Escolhido por
ser um domínio único e coeso, sem necessidade de escalar partes de forma independente —
microsserviços aqui adicionariam complexidade de rede e operação sem benefício real para
o escopo do desafio. Justificativa completa em `DECISIONS.md`.



## Estratégia de Ambiente

**Desenvolvimento e testes:** H2 em memória, para validação rápida do core sem depender de
infraestrutura externa. **Esta é a única configuração testada e validada até o momento** —
todos os testes automatizados (unitários e de integração via `@SpringBootTest`) rodam e
passam contra H2.

**Produção / ambiente definitivo:** PostgreSQL, orquestrado via Docker Compose
(`docker-compose.yml` + `Dockerfile` multi-stage: build com Maven, runtime só com JRE).

> ⚠️ **Status atual: configuração Postgres implementada, ainda não validada de ponta a
> ponta.** O `docker-compose.yml` sobe os dois serviços (`postgres` + `app`) com
> healthcheck e `depends_on: service_healthy`, e o `application.properties` já tem a URL,
> driver (`org.postgresql.Driver`) e credenciais corretas — mas a execução completa da
> aplicação contra um Postgres real (dentro do container) ainda está em processo de
> confirmação. Esta seção será atualizada assim que a validação for concluída.

> ✅ **Atualização:** validação concluída com sucesso após a correção de um bug
> específico do Postgres na query do extrato analítico (ver `DECISIONS.md`, seção
> "Bug encontrado ao validar o extrato contra Postgres real"). Os 21 cenários da
> coleção Postman (11 originais + 10 dos 3 serviços novos) passam contra Postgres
> real via Docker Compose.

### Como alternar entre os bancos

O projeto usa uma única fonte de configuração (`application.properties`) com apenas um
banco ativo por vez — comente o bloco que não for usar:

```properties
# --- H2 (dev local) ---
# spring.datasource.url=jdbc:h2:mem:creditengine
# ...

# --- Postgres (Docker) ---
spring.datasource.url=jdbc:postgresql://localhost:5432/creditengine
# ...
```

### Rodando via Docker

```bash
    docker compose up --build
```

Sobe o Postgres e a aplicação juntos, orquestrados — atende ao requisito de "Docker +
Docker Compose orquestrando aplicação e banco" do desafio técnico.
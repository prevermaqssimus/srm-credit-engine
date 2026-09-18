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

**Próximas vezes, sem mudança de código** (mais rápido, reaproveita a imagem já construída):
```bash
docker compose up
```

**Parar tudo:**
```bash
docker compose down
```

**Parar e apagar os dados do Postgres também** (recomeça do zero):
```bash
docker compose down -v
```

---

## Como Rodar

Não existe escolha livre de banco — é uma correspondência fixa: rodar local usa H2,
rodar via Docker usa Postgres.

### Opção 1 — Local (Maven direto, sem Docker) → usa H2

Pré-requisito: Java 21 e Maven instalados.

```bash
./mvnw clean install
./mvnw spring-boot:run
```

Sobe com H2 em memória (configuração padrão do `application.properties` para ambiente
local). Não precisa instalar nem configurar nenhum banco separadamente.

Confirme que subiu:
```
http://localhost:8080/swagger-ui/index.html#/
http://localhost:8080/actuator/health 
```
Deve responder `{"status":"UP"}`.

### Opção 2 — Via Docker Compose → usa Postgres

Ver "Rodando via Docker" acima. Mesma checagem de saúde, agora respondendo do container.

### Rodando os testes

```bash
./mvnw test
```

Sempre roda contra H2, independente de qual ambiente (local ou Docker) estiver configurado
para execução normal — os testes nunca tocam o Postgres.

---

## Documentação Complementar

A pasta [`DOCS/`](./DOCS) reúne a documentação de apoio:

- **[`DOCS/architecture/SERVICES.md`](./DOCS/architecture/SERVICES.md)** — explicação de
  cada endpoint da API e a regra de negócio por trás dele. Ponto de partida recomendado
  para quem quer entender a API sem ler o código-fonte inteiro.
- **[`DOCS/adr/`](./DOCS/adr)** — ADRs (Architecture Decision Records) das decisões
  estruturais (ex.: monólito modular vs. microsserviços, PostgreSQL como banco definitivo).
- **[`DOCS/postman/`](./DOCS/postman)** — coleção Postman pronta para importar
  (`srm-credit-engine-postman-collection.json`), com um guia de uso em `API_TESTING.md`.

Para as regras de negócio formais (precisão numérica, arredondamento, critérios de
aceite), ver `SPEC.md`. Para decisões de corte de escopo, ver `DECISIONS.md`.

---

## Frontend (Painel do Operador)

O frontend é um projeto Angular **separado**, em outro repositório
(`srm-credit-engine-frontend`) — não faz parte deste repositório.

**Para rodar o frontend localmente**, com este backend já no ar (Opção 1 ou 2 acima):

```bash
# dentro do repositório do frontend
npm install
npm start
```

Abre em `http://localhost:4200`.

> ⚠️ **A porta 4200 é obrigatória.** O CORS deste backend está configurado para aceitar
> requisições especificamente de `http://localhost:4200` (ver `CorsConfig.java`). Se essa
> porta estiver ocupada e o Angular subir em outra, toda chamada à API retorna
> `403 Forbidden`. Instruções completas de setup, troubleshooting e decisões de escopo do
> frontend estão no `README.md` e `DECISIONS.md` daquele repositório.

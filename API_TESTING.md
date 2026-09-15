# Testando a API — Postman e Swagger

## Documentação interativa (Swagger / OpenAPI)

Com a aplicação rodando (local ou via Docker), a documentação interativa dos endpoints
fica disponível em:

- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Especificação OpenAPI (JSON):** `http://localhost:8080/v3/api-docs`

> ⚠️ Isso assume a dependência `springdoc-openapi-starter-webmvc-ui` no `pom.xml`. Se
> ainda não estiver adicionada, os caminhos acima retornam 404 — nesse caso, é só
> adicionar a dependência e reiniciar a aplicação para os endpoints ficarem disponíveis.

## Como importar no Postman

A coleção está versionada dentro do próprio repositório, em:

```
docs/postman/SRM-Credit-Engine.postman_collection.json
```

1. Abra o Postman
2. **File → Import** (ou o botão **"Import"** no canto superior esquerdo)
3. Arraste o arquivo `docs/postman/SRM-Credit-Engine.postman_collection.json` (ou navegue até ele pelo seletor de arquivos)
4. A coleção **"SRM Credit Engine"** aparece na sua lista, já organizada em 5 pastas

### O que está dentro — 21 requisições, organizadas em 5 pastas

**📁 Recebíveis**
- Cadastrar (Duplicata Mercantil, BRL)
- Cadastrar (Cheque Pré-datado, USD)
- Cadastrar inválido — testa a validação (400)
- Buscar por ID
- Buscar inexistente — testa 404
- Listar todos

**📁 Simulação**
- Simular recebível em BRL
- Simular recebível em USD — chama a API real de câmbio
- Simular com payload inválido — testa a validação (400)

**📁 Currency Engine**
- Consultar taxa vigente USD → BRL
- Consultar taxa vigente (usando defaults, sem query params)

**📁 Liquidações**
- Liquidar em BRL
- Liquidar em USD — chama a API real de câmbio
- Liquidar de novo, mesma chave — testa idempotência
- Liquidar recebível inexistente — testa 404
- Liquidar recebível já liquidado — testa 409
- Buscar liquidação por ID

**📁 Extrato Analítico**
- Extrato completo, sem filtro
- Extrato filtrado por moeda
- Extrato filtrado por cedente
- Extrato filtrado por período
- Extrato paginado

### Variáveis já configuradas

| Variável | Valor padrão | Observação |
|---|---|---|
| `{{baseUrl}}` | `http://localhost:8080` | edite se sua porta for diferente |
| `{{receivableId}}` | `1` | edite depois de criar seu primeiro recebível, se o ID vier diferente |
| `{{settlementId}}` | `1` | idem, ajuste conforme o ID retornado |

Pra editar essas variáveis: clique na coleção → aba **"Variables"**.

### Ordem sugerida pra testar

1. Cadastrar recebível (Duplicata Mercantil, BRL) → anote o `id` retornado
2. Atualize a variável `{{receivableId}}` com esse ID, se for diferente de `1`
3. Liquidar em BRL
4. Liquidar de novo com a **mesma chave** → confirme que retorna o mesmo `settlement`
5. Explore o resto — os cenários de erro (400, 404, 409) já estão prontos também
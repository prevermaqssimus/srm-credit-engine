Testando a API — Postman e Swagger
Documentação interativa (Swagger / OpenAPI)

Com a aplicação rodando (local ou via Docker), a documentação interativa dos endpoints fica disponível em:

Swagger UI: http://localhost:8080/swagger-ui/index.html
Especificação OpenAPI (JSON): http://localhost:8080/v3/api-docs

⚠️ Isso assume a dependência springdoc-openapi-starter-webmvc-ui no pom.xml. Se ainda não estiver adicionada, os caminhos acima retornam 404 — nesse caso, é só adicionar a dependência e reiniciar a aplicação para os endpoints ficarem disponíveis.

Como importar no Postman

A coleção está versionada dentro do próprio repositório, em:

docs/postman/SRM-Credit-Engine.postman_collection.json
Abra o Postman
File → Import (ou o botão "Import" no canto superior esquerdo)
Arraste o arquivo docs/postman/SRM-Credit-Engine.postman_collection.json (ou navegue até ele pelo seletor de arquivos)
A coleção "SRM Credit Engine" aparece na sua lista, já organizada em 5 pastas
O que está dentro — 21 requisições, organizadas em 5 pastas

📁 Recebíveis

Cadastrar (Duplicata Mercantil, BRL)
Cadastrar (Cheque Pré-datado, USD)
Cadastrar inválido — testa a validação (400)
Buscar por ID
Buscar inexistente — testa 404
Listar todos

📁 Simulação

Simular recebível em BRL
Simular recebível em USD — chama a API real de câmbio
Simular com payload inválido — testa a validação (400)

📁 Currency Engine

Consultar taxa vigente USD → BRL
Consultar taxa vigente (usando defaults, sem query params)

📁 Liquidações

Liquidar em BRL
Liquidar em USD — chama a API real de câmbio
Liquidar de novo, mesma chave — testa idempotência
Liquidar recebível inexistente — testa 404
Liquidar recebível já liquidado — testa 409
Buscar liquidação por ID

📁 Extrato Analítico

Extrato completo, sem filtro
Extrato filtrado por moeda
Extrato filtrado por cedente
Extrato filtrado por período
Extrato paginado
Variáveis já configuradas
Variável	Valor padrão	Observação
{{baseUrl}}	http://localhost:8080	edite se sua porta for diferente
{{receivableId}}	1	edite depois de criar seu primeiro recebível, se o ID vier diferente
{{settlementId}}	1	idem, ajuste conforme o ID retornado

Pra editar essas variáveis: clique na coleção → aba "Variables".

Ordem sugerida pra testar
Cadastrar recebível (Duplicata Mercantil, BRL) → anote o id retornado
Atualize a variável {{receivableId}} com esse ID, se for diferente de 1
Liquidar em BRL
Liquidar de novo com a mesma chave → confirme que retorna o mesmo settlement
Explore o resto — os cenários de erro (400, 404, 409) já estão prontos também
Testando a Observabilidade (Actuator / Micrometer)

Depois de testar a API de negócio (as 5 pastas acima), há uma segunda coleção, separada, dedicada só à observabilidade — infraestrutura de operação (Actuator/ Micrometer), não endpoints de negócio, por isso fica num arquivo à parte.

Mesmo caminho da coleção principal, nome diferente:

docs/postman/srm-credit-engine-observabilidade-postman-collection.json

Importe do mesmo jeito (File → Import) — aparece como uma coleção separada, "SRM Credit Engine - Observabilidade", com 4 requisições:

Health check — GET /actuator/health
Listar todas as métricas disponíveis — GET /actuator/metrics
Métrica: liquidações completadas (contador) — GET /actuator/metrics/settlements.completed
Métrica: duração do motor de precificação (timer) — GET /actuator/metrics/pricing.calculation.duration

Importante — rode essa coleção só DEPOIS da principal: as 2 métricas de negócio (settlements.completed e pricing.calculation.duration) começam zeradas (COUNT: 0) até a aplicação processar pelo menos uma simulação ou liquidação real.
Rode primeiro as pastas Simulação e Liquidações da coleção principal, e só depois abra a coleção de Observabilidade para ver os números refletindo o uso real.
# DECISIONS.md — SRM Credit Engine

## Decisão de processo: default branch e merges iniciais

O default branch do repositório estava configurado como `main`, por isso os primeiros commits foram mesclados nela. Ao perceber isso, corrigi o default branch para `develop`, mas decidi não desfazer os merges já feitos em `main` — optei por manter o histórico como está, sem reescrever, para não perder o registro do que aconteceu. A partir daquele ponto, passei a mesclar em `develop`, seguindo o fluxo corretamente.

Passos 3, 4 e 5 entregues juntos, na mesma branch

O plano original previa o Passo 3 (GoldenCasesTest, vermelho de propósito) commitado sozinho, antes das outras peças existirem. Decidi entregar os Passos 3, 4 (entidades) e 5 (Strategy Pattern + PricingService) juntos, na mesma branch, porque o Passo 3 sozinho deixa o projeto sem compilar — e os Passos 4 e 5 são exatamente as peças que resolvem isso. Nada do escopo mudou, só a forma de entrega: em vez de um commit vermelho isolado seguido de outro depois, um único PR já traz o teste e o que o faz passar.

## Currency Engine com um único provedor (não dois, como a SPEC.md Seção 5 descreve)

A SPEC.md Seção 5 descreve dois provedores de câmbio (primário + secundário) com circuit breaker decidindo qual usar. Implementamos apenas um provedor real (open.er-api.com), com retry e circuit breaker configurados na classe `ApiExchangeRateProvider`. Até o momento desta entrada, essa classe ainda não está conectada a nenhum fluxo de liquidação real (isso depende do `SettlementService`, ainda não implementado) — o comportamento de retry/circuit breaker existe no código, mas ainda não foi exercitado em um fluxo completo de ponta a ponta. Implementar dois provedores exigiria uma segunda fonte de câmbio real e a lógica de troca entre eles, dobrando a complexidade para um ganho que não é demonstrável no volume deste case.

## H2 e PostgreSQL configurados no mesmo arquivo — ambos validados de ponta a ponta

A aplicação usa uma única fonte de configuração (application.properties), com apenas um banco ativo por vez — o bloco que não está em uso fica comentado.

H2 foi validado via testes automatizados (unitários e de integração, @SpringBootTest) — todos os 23 testes do projeto passam contra H2, cobrindo golden cases, idempotência e concorrência.

PostgreSQL foi validado de ponta a ponta via docker compose up, contra um Postgres real, através da coleção completa do Postman (11 requisições): cadastro de recebíveis (BRL e USD), validação de input inválido (400), busca por ID e listagem, liquidação em BRL, liquidação em USD com conversão cambial real (chamada de fato ao provedor de câmbio), idempotência (mesma chave não gera liquidação duplicada), e os cenários de erro (recebível inexistente → 404, recebível já liquidado → 409).

Ambas as configurações estão confirmadas funcionando — H2 no fluxo de desenvolvimento e testes, Postgres no fluxo de execução real via Docker Compose (docker-compose.yml orquestrando os serviços app + postgres, com healthcheck e depends_on: service_healthy).

Alternar entre os dois bancos é uma ação manual (comentar/descomentar o bloco no arquivo), não automática por perfil — ver ADR 0002 para a justificativa dessa escolha e as alternativas avaliadas.


## 3 serviços especificados de forma incompleta na SPEC inicial

Ao começar o frontend, percebi que 3 endpoints que o frontend precisava consumir
(simulação em tempo real, consulta de câmbio, extrato analítico) nunca tinham sido
implementados — e, olhando de volta, o `SPEC.md` original também nunca tinha
especificado *como* cada um deveria funcionar (só citava o resultado esperado nos
Critérios de Aceite 5 e 6, sem desenhar o endpoint em si).

Implementei os 3 numa única branch (`feature/currency-extrato-simulacao`, a partir de
`develop`) e completei a especificação técnica de cada um diretamente no `SPEC.md`
(Seção 5 e Critérios 5/6), marcada como adendo posterior à redação inicial.
# DECISIONS.md — SRM Credit Engine

## Decisão de processo: default branch e merges iniciais

O default branch do repositório estava configurado como `main`, por isso os primeiros commits foram mesclados nela. Ao perceber isso, corrigi o default branch para `develop`, mas decidi não desfazer os merges já feitos em `main` — optei por manter o histórico como está, sem reescrever, para não perder o registro do que aconteceu. A partir daquele ponto, passei a mesclar em `develop`, seguindo o fluxo corretamente.

Passos 3, 4 e 5 entregues juntos, na mesma branch

O plano original previa o Passo 3 (GoldenCasesTest, vermelho de propósito) commitado sozinho, antes das outras peças existirem. Decidi entregar os Passos 3, 4 (entidades) e 5 (Strategy Pattern + PricingService) juntos, na mesma branch, porque o Passo 3 sozinho deixa o projeto sem compilar — e os Passos 4 e 5 são exatamente as peças que resolvem isso. Nada do escopo mudou, só a forma de entrega: em vez de um commit vermelho isolado seguido de outro depois, um único PR já traz o teste e o que o faz passar.

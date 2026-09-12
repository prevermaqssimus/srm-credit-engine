# SPEC.md — SRM Credit Engine (Edição AI-native v2)

Este documento estabelece as premissas, decisões arquiteturais, de precisão numérica e critérios de aceite para o desenvolvimento do **SRM Credit Engine**, garantindo alinhamento técnico e de negócio antes da codificação do core.

---

## 1. Premissas Adotadas (Resolução de Ambiguidades)

* **Unidade do Prazo:** O prazo é expresso estritamente em **meses inteiros** (`Integer`), aplicando-se o regime de juros compostos mensais.
* **Origem e Valor da Taxa Base:** A taxa base padrão é fixada em **1,00% a.m.** (conforme os *golden cases* [casos de referência/aferição]), sendo injetada e gerenciada via arquivo de configuração externo (`application.properties`), permitindo alteração dinâmica sem recompilação.
* **Política de Arredondamento:** Utiliza-se estritamente o **Banker's Rounding (Half-Even)** [Arredondamento Bancário, ou Arredondamento para o Par Mais Próximo] (`RoundingMode.HALF_EVEN`), com precisão de 2 casas decimais. Cálculos intermediários preservam escala estendida (alta precisão) para evitar desvios cumulativos, com o arredondamento final aplicado apenas nos pontos abaixo:
    - **Operações em moeda única (BRL):** arredondamento ocorre uma única vez, sobre o valor presente final.
    - **Operações *cross-currency* [conversão entre moedas] (BRL → USD):** arredondamento ocorre em **duas etapas**: (1) o valor presente em BRL é calculado com precisão estendida e arredondado para 2 casas antes da conversão; (2) o valor já convertido para USD é arredondado novamente para 2 casas. Essa sequência é a que reproduz o *golden case* C3 e deve ser tratada como regra de negócio, não como detalhe livre de implementação.
* **Taxa de Câmbio na Liquidação:** Para operações *cross-currency* (título em BRL, pagamento em USD), utiliza-se a **taxa de câmbio vigente com o *timestamp* [carimbo de data/hora] mais recente** cadastrada no `Currency Engine` (Motor de Câmbio) no exato momento da efetivação da transação.

---

## 2. Perguntas ao Negócio (Contexto Real)

Se este fosse um ambiente produtivo real, as seguintes diretrizes seriam validadas com a mesa de operações:

1. Qual é o provedor oficial de feeds de câmbio para produção (ex.: Bloomberg, BCB, Reuters), já que a API pública utilizada neste case (ver DECISIONS.md) é adequada para o escopo do desafio, mas não teria o *SLA* [Acordo de Nível de Serviço] e a governança exigidos por uma operação financeira real?
2. Existe um limite máximo de alçada ou exposição por cedente que exija um fluxo de aprovação de dois fatores (*maker-checker* [elaborador-verificador, dupla checagem]) antes da liquidação efetiva?
3. Em caso de falha simultânea de todos os provedores de câmbio configurados (ver seção 5), o sistema já rejeita a liquidação com erro explícito e dispara um alerta técnico de indisponibilidade — decisão de engenharia adotada para nunca liquidar com taxa desatualizada ou depender de intervenção manual no caminho crítico.
4. Qual o canal e o *SLA* de resposta esperado para esse alerta técnico (ex.: notificação à mesa de operações, integração com sistema de monitoramento)?
5. Uma falha prolongada de todos os provedores de câmbio configura um incidente operacional (mesma categoria de gravidade do cenário do Anexo B). A política de comunicação com cedentes afetados — se, quando e como notificá-los — segue o protocolo de resposta a incidentes da mesa de operações, não é uma decisão tomada em tempo real pelo sistema.
6. O endpoint de "atualização manual de taxas" (requisito 4.1.1) não foi implementado como escrita livre, por representar risco de manipulação de preço (uma taxa poderia ser inserida artificialmente sem verificação contra o mercado real). Em caso de falha de todos os provedores automáticos, o sistema não permite inserção manual de taxa em nenhum cenário — a liquidação é rejeitada com mensagem explícita de indisponibilidade do serviço de câmbio, e um alerta técnico é disparado
7. (ver item 3). Essa decisão é consistente com o Critério de Aceite 
8. 4 (Auditabilidade): nenhuma liquidação já registrada é alterada, e nenhuma exceção manual é aberta no caminho crítico.

## 3. Decisões de Precisão Numérica e Dados

* **Na Aplicação:** Uso obrigatório de `java.math.BigDecimal` em todas as representações monetárias, taxas e cálculos de deságio e valor presente. Proibição absoluta de tipos de ponto flutuante primitivos (*float*/*double*) [tipos numéricos de precisão aproximada].
* **Precisão de cálculo vs. precisão de armazenamento (distinção explícita):**
    - **Cálculo em memória:** *MathContext* [Contexto Matemático] com precisão estendida (mínimo 10 dígitos significativos) durante os passos intermediários da fórmula.
    - **Armazenamento em banco:** escala fixa, definida abaixo — não confundir com a precisão de cálculo, que é maior.
* **No Banco de Dados (Relacional):**
    * Colunas de valores financeiros (`face_value`, `present_value`, `discount`) mapeadas como `NUMERIC(19, 4)` para máxima fidelidade durante processamentos e `NUMERIC(19, 2)` para saldos finais consolidados.
    * Colunas de taxas percentuais mapeadas como `NUMERIC(10, 6)`.
    * Colunas de taxas de câmbio mapeadas como `NUMERIC(19, 6)`.
* **Transacionalidade:** Garantia de isolamento rigoroso via transações *ACID* [Atomicidade, Consistência, Isolamento e Durabilidade] (`@Transactional` no Spring) e blindagem contra concorrência e duplicidade por meio de controle de idempotência (restrição de unicidade no banco) nas requisições de liquidação.

## 4. Critérios de Aceite

1. **Corretude Matemática:** O motor de precificação deve reproduzir com exatidão de centavo os 3 *golden cases* (casos de referência/aferição) oficiais (C1, C2 e C3), validados por teste unitário automatizado que executa o motor de cálculo isoladamente, sem dependência de infraestrutura (banco de dados ou API [Interface de Programação de Aplicações]).
2. **Idempotência:** Requisições duplicadas (por *retry* [nova tentativa] de rede ou duplo clique) com a mesma chave de idempotência não podem gerar novas liquidações ou alterar o estado do recebível indevidamente.
3. **Resiliência e Segurança:** Tratamento global de exceções, validação estrita de *payloads* (corpos de requisição) via *Bean Validation* (biblioteca de validação de dados do Java), e proteção contra concorrência (ex.: *Optimistic Locking* (Bloqueio Otimista) via `@Version` nas entidades).
4. **Auditabilidade:** Toda liquidação gera um registro imutável com rastreabilidade completa (*timestamp* (carimbo de data/hora), taxa de câmbio efetiva, provedor que respondeu, usuário/sistema e valores nominais). Alterar uma liquidação registrada não é uma operação do sistema, em nenhum cenário.
5. **Usabilidade:** A simulação de valor líquido no painel do operador responde em até 300ms, sem quebrar o fluxo de digitação. O operador consegue simular um recebível sem necessidade de treinamento ou consulta a documentação externa.
6. **Desempenho:** O endpoint de liquidação responde em até 1s sob carga normal, incluindo a consulta de câmbio em tempo real. A rota de extrato de liquidação com filtros suporta paginação *server-side* (paginação no lado do servidor) sem degradação perceptível na escala de dados esperada para o case.enho:** O endpoint de liquidação responde em até 1s sob carga normal, incluindo a consulta de câmbio em tempo real. A rota de extrato de liquidação com filtros suporta paginação server-side sem degradação perceptível na escala de dados esperada para o case.


## 5. Resiliência do Currency Engine (Motor de Câmbio) — Câmbio em Tempo Real

- **Consulta em tempo real:** cada liquidação *cross-currency* (conversão entre moedas) consulta a taxa vigente no momento exato da efetivação, via chamada síncrona (bloqueante, que aguarda resposta antes de continuar) a um provedor externo real.

- **Dois provedores com *circuit breaker* (disjuntor de circuito):** um provedor primário é consultado por padrão. Um *circuit breaker* monitora falhas/*timeouts* (tempos limite excedidos) consecutivos (limite configurável, ex.: 3 falhas em 10s). Ao abrir, as chamadas são redirecionadas para o provedor secundário, com tentativa periódica de *half-open* (meio-aberto, estado de teste de recuperação) no primário (ex.: a cada 30s). *Timeout* curto por chamada + nova tentativa (*retry*) com espera exponencial crescente (*backoff exponencial*) (máximo N tentativas) antes de considerar falha.

- **Falha total (ambos os provedores indisponíveis):** a liquidação é rejeitada com erro explícito, e um alerta técnico é disparado. Não há reversão silenciosa (*fallback* silencioso) para taxa desatualizada em memória temporária (*cache*), nem intervenção manual no caminho crítico.

- **Auditoria da origem da taxa:** cada liquidação persiste a taxa, o *timestamp* (carimbo de data/hora) e qual dos dois provedores respondeu.

- **Nota de escala:** este modelo (chamada síncrona por transação) é adequado ao escopo do *case* (desafio/estudo de caso), mas não escala para o cenário de 1 milhão de transações/minuto (nível Staff/*Tech Lead* [Líder Técnico]). Nesse cenário, a arquitetura migraria para memória temporária (*cache*) com tempo de vida curto (*TTL* — *Time To Live*) validado por verificação de desatualização (*staleness check*), mantendo os dois provedores/*circuit breaker* como fonte de atualização do *cache* — decisão de custo-benefício (*trade-off*) documentada em `DECISIONS.md`.


## 6. Escopo e Nível Visado

Esta *SPEC* (Especificação) assume metas de nível **Sênior** (*Optimistic Locking* [Bloqueio Otimista], resiliência de câmbio, observabilidade básica). Qualquer redução de escopo por restrição de tempo é declarada explicitamente em `DECISIONS.md`, para que não haja contradição entre o que a *SPEC* promete e o que o código efetivamente entrega.
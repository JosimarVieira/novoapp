# Prompt para iniciar a Etapa 2a

Contexto: a Etapa 1 (bot Telegram + despesa, esqueleto andante) está fechada e
mergeada em `main`. Este prompt é para uma sessão nova do Claude Code
continuar o mesmo repositório, agora construindo a Etapa 2a do ROADMAP.

## Leitura obrigatória, nesta ordem

1. `CLAUDE.md` inteiro — regras não negociáveis, especialmente a 6 (curto-
   circuito determinístico) e a 7 (todo lançamento é reversível).
2. `ROADMAP.md`, seção **Etapa 2a** — é o escopo desta etapa, já reescrito em
   2026-09-07 pra refletir as ADRs 0024-0026. Leia a seção inteira, não só o
   parágrafo do "Entregável".
3. `docs/02-arquitetura/modelo-de-dados.md`, seção `pending_action` — schema
   completo (`id, household_id, member_id, channel_identity_id, intent_json,
   question_asked, options_json, expires_at, resolved_at, resolution`). Não
   existe no banco ainda: `V1__initial_schema.sql` termina em `transaction` e
   nos grants de `inbound_message`. A primeira migration desta etapa cria essa
   tabela.
4. Os cinco SDDs de módulo (`docs/02-arquitetura/sdd-modulo-*.md`) — leia a
   seção "Gatilhos de revisão" de cada um, é o que aponta o que a Etapa 1
   deixou pendurado pra esta etapa.
5. `docs/03-specs/features/financas-lancamento-por-chat.feature`, cenários
   `@etapa2` (são 13, incluindo os 5 novos de categoria/hierarquia/desfazer).
   `docs/03-specs/features/mercado-lista-de-compras.feature` inteiro — a tag
   `@etapa2` está no nível da `Funcionalidade`, os nove cenários são todos
   desta etapa. `docs/03-specs/features/vinculo-de-identidade.feature`,
   cenário "OWNER convida um novo membro" (`@etapa2`).
6. ADRs, nesta ordem de relevância: **0004** (política de confiança —
   agora você implementa a linha "confiança média/baixa" que a Etapa 1 pulou),
   **0018** (central de pendências — o mecanismo genérico de `PendingAction`),
   **0024** (categoria sugerida por texto livre), **0026** (hierarquia na
   criação de categoria), **0025** (desfazer: precedência e escopo), **0016**
   (subcategoria, limite de um nível), **0012** (edição/estorno entre membros
   — é de onde a 0025 herda o escopo household-wide), **0013** (household
   sem categoria), **0020** (convite de membro), **0023** (descrição do
   lançamento — aceita mas **ainda não implementada**: conferi
   `RegisterExpenseTool.java` agora e o parâmetro `descricao` não existe no
   código, só na ADR).
7. `server/src/test/java/com/novoapp/acceptance/Etapa2AcceptanceTest.java` —
   está com `@Disabled`, glue em `com.novoapp.acceptance`, filtra por tag
   `@etapa2`. Tirar o `@Disabled` é o primeiro passo de código desta etapa,
   não o último — assim os cenários aparecem falhando por passo indefinido
   desde o início, e você vê o placar subir cenário a cenário.
8. `server/src/test/java/com/novoapp/acceptance/ExpenseByChatSteps.java` e
   `IdentityLinkSteps.java` — glue existente da Etapa 1, pra seguir a mesma
   convenção (anotações em português `@Dado`/`@Quando`/`@Entao`/`@E`, regex
   por passo, `AcceptanceWorld` + `Fixtures` injetados, `@Before` resetando
   entre cenários).

## O que construir

- Migration Flyway pra `pending_action` (schema exato em `modelo-de-dados.md`,
  RLS seguindo o mesmo padrão de `transaction`/`category`).
- O mecanismo genérico de `PendingAction` da ADR-0018: pergunta com opções
  numeradas, resolução por curto-circuito (`sim`, `não`, número), TTL como
  config global provisória (10 min, [decisão aberta #8](docs/DECISOES-ABERTAS.md) — não invente
  calibração agora).
- A política de confiança média/baixa completa da ADR-0004 (a Etapa 1 só
  implementou confiança alta).
- Categoria sugerida (ADR-0024): parâmetro `categoria_sugerida` em
  `registrarDespesa`, mutuamente exclusivo com o enum `categoria`; schema
  dinâmico que omite a propriedade `categoria` quando o household tem zero
  categorias de despesa.
- Hierarquia (ADR-0026): terceira via de resposta à `PendingAction` de
  categoria (correção livre além de sim/não), tool dedicada
  `confirmarCategoriaSugerida` (`nome` + `categoria_pai` opcional), criação de
  categoria-pai e subcategoria juntas quando faltarem as duas, recusa quando
  a correção tentaria empilhar dois níveis (ADR-0016).
- Desfazer com precedência (ADR-0025): `PendingAction` aberta sempre vence
  sobre estorno; sem pendência, estorna a transação não estornada mais
  recente do household inteiro (qualquer membro), sem janela de tempo.
- Descrição do lançamento (ADR-0023): parâmetro opcional `descricao` em
  `registrarDespesa`, fora da política de confiança, nunca vira pergunta.
- Mercado: tools `adicionarItemLista`, `marcarItemComprado`, `consultarLista`
  e o glue novo (arquivo próprio, não adaptação de `ExpenseByChatSteps`).
- Emissão de convite pelo OWNER (ADR-0020) — só o lado de quem convida; o
  lado de quem aceita já existe desde a Etapa 1 (`IdentityLinkSteps`).

**Entregável** (copiado do ROADMAP, é o critério de pronto): `acabou o arroz`
entra na lista, `o que está faltando?` responde, `pet shop 80` oferece criar
a categoria e grava depois do `sim`, `restaurante eu e esposa 90` corrigido
para "dentro de alimentação" cria a hierarquia certa mesmo quando nada existe
ainda, e `desfazer` com pergunta pendente cancela a pergunta em vez de
estornar. Os 22 cenários `@etapa2` verdes.

## O que explicitamente NÃO construir agora

- O elo lista → despesa (fechar compra, gerar lançamento a partir da lista) —
  é a Etapa 3, tem `.feature` própria (`elo-fechamento-de-compra.feature`,
  tag `@etapa3`) que não faz parte desta etapa.
- Tarefas e agenda — Etapa 2b, depois da Etapa 3, `.feature` ainda nem
  escrita.
- Qualquer coisa de web/PWA/auth (Etapa 4), cartão/fatura, meta financeira.
- Calibração real de limiar de confiança e TTL — ficam config editável e
  redeployável, nunca constante escondida no código nem preferência por
  household (ver ADR-0004 e [decisão aberta #7/#8](docs/DECISOES-ABERTAS.md)).
- Verificação de posse do e-mail e recuperação de senha (ADR-0021) — fora de
  escopo até a Etapa 6.

## Decisões estruturais que assumi — corrija se discordar

- `PendingAction` (entidade/repositório) vive em `conversation`, não em
  `finance` nem em módulo técnico próprio: é `conversation` quem pergunta e
  resolve por chat, mesmo quando o conteúdo da pendência é financeiro
  (categoria) ou de mercado (item ambíguo). `finance`/`shopping` só recebem o
  resultado já resolvido, do mesmo jeito que hoje só recebem `Intent` depois
  de `nlu` interpretar.
- `confirmarCategoriaSugerida` é tool nova em `nlu.tools`, ao lado de
  `RegisterExpenseTool`, não um parâmetro a mais nele — o contexto da chamada
  é a pendência aberta, não uma mensagem de despesa nova.
- Convite de membro (ADR-0020) reaproveita `OutboundMessagePort` já existente
  (`identity.spi`) pra mandar o link — sem canal novo.

## Uma decisão de implementação que os SDDs deixaram em aberto de propósito

A ADR-0018 desenha `PendingAction` como mecanismo genérico: qualquer pendência
resolve por `sim`/`não`/número via curto-circuito. A ADR-0026 abre uma
exceção só para pendência de criação de categoria — resposta que não é
`sim`/`não`/`desfazer` vira correção livre, processada por segunda chamada ao
modelo. Isso significa que o código, ao receber uma resposta que não bate com
nenhum atalho, precisa saber **que tipo** de `PendingAction` está em aberto
pra decidir se cai no "não entendi" genérico ou na tool de correção. O schema
de `pending_action` não tem coluna de tipo/discriminador — só `intent_json`,
que guarda o que gerou a pergunta.

Decida como o código distingue isso (uma tag dentro de `intent_json`, um
campo calculado a partir do formato de `options_json`, ou outra forma) e
registre a decisão de volta em `sdd-modulo-conversation.md`, na mesma seção
"Escopo desta versão" — mesmo padrão já usado na Etapa 1 pro pacote do
interceptor de RLS.

## Regra de trabalho

Pare e pergunte em qualquer lacuna ou contradição que encontrar entre ADR,
SDD, `.feature` e código — não resolva em silêncio. Este projeto já pegou
mais de uma contradição entre documentos aceitos (a mais recente: ADR-0004 x
ADR-0013 sobre criação de categoria, resolvida pelas ADRs 0024/0026) — o
padrão esperado é perguntar antes de assumir, não descobrir depois em
produção.

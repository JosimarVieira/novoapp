# Prompt para iniciar a Etapa 1 (bot Telegram + despesa)

Cole este arquivo inteiro como primeira mensagem numa sessão nova do Claude
Code, aberta na raiz deste repositório (`novoapp/`).

---

Você vai iniciar do zero a implementação de um projeto novo. Não existe
código ainda — só documentação. Antes de escrever qualquer linha, leia
nesta ordem:

1. `CLAUDE.md` — produto, stack, regras não negociáveis, convenções de
   código (comentário em português, identificador em inglês).
2. `ROADMAP.md`, seção "Etapa 1" — o escopo exato desta etapa. Foi
   corrigido em 2026-09-05 pra excluir ambiguidade, criação de categoria e
   desfazer — isso é Etapa 2, não implemente ainda mesmo que pareça fácil.
3. `docs/02-arquitetura/modelo-de-dados.md` — schema completo, com diagrama
   Mermaid. Leia até as tabelas que esta etapa não usa ainda (`invoice`,
   `financial_goal`, `shopping_list`, `task`...) — não implemente essas
   tabelas agora, mas o schema Flyway desta etapa não pode desenhar algo
   incompatível com o que vem depois.
4. `docs/02-arquitetura/sdd-visao-geral.md` — módulos, fronteiras, regra de
   dependência entre eles. Isso vira teste ArchUnit, não é sugestão.
5. Os cinco SDDs de módulo que esta etapa cria — leia a seção **"Escopo
   desta versão"** de cada um com atenção, é o que delimita o que NÃO
   fazer ainda:
   - `docs/02-arquitetura/sdd-modulo-channel.md`
   - `docs/02-arquitetura/sdd-modulo-identity.md`
   - `docs/02-arquitetura/sdd-modulo-nlu.md`
   - `docs/02-arquitetura/sdd-modulo-conversation.md`
   - `docs/02-arquitetura/sdd-modulo-finance.md`
6. `docs/03-specs/features/financas-lancamento-por-chat.feature` — **só os
   cenários marcados `@etapa1`**. Ignore os `@etapa2` por enquanto (viram
   testes que devem falhar por "não implementado", não passar por acidente
   nem ser implementados adiantado).
7. `docs/03-specs/features/vinculo-de-identidade.feature` — fluxo de
   onboarding completo, usado quando a mensagem vem de um número não
   vinculado.
8. ADRs referenciadas nos documentos acima, sempre que precisar entender o
   porquê de uma decisão — pelo menos: ADR-0001 (Quarkus), ADR-0003 (RLS),
   ADR-0004 (function calling + política de confiança), ADR-0005
   (idempotência), ADR-0007 (múltiplos households), ADR-0009 (Mistral),
   ADR-0019 (conta padrão por membro), ADR-0020 (convite de membro).

## O que construir

Os cinco módulos: `channel`, `identity`, `nlu`, `conversation`, `finance`
(mais `shopping` e `tasks` como pacotes vazios/stub, só pra estrutura e
teste ArchUnit existirem desde já). Schema Flyway com `household`,
`member`, `household_membership`, `channel_identity`, `household_invite`,
`inbound_message`, `account`, `category`, `transaction` — RLS ativa em
todas exceto `member` (ver ADR-0003 e ADR-0007 pra exceção de
`inbound_message`).

Entregável final (é o critério de pronto, ver ROADMAP): você manda
`mercado 50` no Telegram, aparece a linha em `transaction` no Postgres, com
recibo de volta no chat. Reentrega da mesma mensagem pelo provedor não
duplica nada. Teste de vazamento de tenant (household A nunca vê dado de
household B) verde.

## O que explicitamente NÃO construir agora (fica pra Etapa 2+)

- `PendingAction`, curto-circuito de confirmação, qualquer pergunta de volta
  pro usuário durante o registro de despesa.
- Ambiguidade entre categorias, criação de categoria nova, valor ausente,
  desfazer (`@etapa2` no `.feature`).
- Cartão/fatura, estorno/edição de lançamento, metas financeiras.
- Autenticação web, PWA — isso é ADR-0021, Etapa 4. Não crie nenhum
  endpoint REST pra Vue agora.
- `shopping`/`tasks` com lógica de verdade — só o pacote existir.

## Decisões estruturais que assumi ao escrever este prompt — corrija se discordar

Nenhum ADR decide isso ainda; são escolhas de conveniência, fáceis de
mudar:

- Código novo em `server/` na raiz do repo, paralelo a `docs/` e `legacy/`.
- Pacote raiz `com.novoapp` (ex.: `com.novoapp.channel`,
  `com.novoapp.identity`).
- Extensões Quarkus sugeridas: `quarkus-rest-jackson` (webhook),
  `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`,
  `quarkus-flyway`, `quarkus-langchain4j-mistral-ai` (ADR-0009),
  `quarkus-arc` (CDI, pro evento `HouseholdCreated`). ArchUnit como
  dependência de teste (`com.tngtech.archunit:archunit-junit5`), não é
  extensão Quarkus.

## Uma decisão de implementação que os SDDs deixaram em aberto de propósito

O interceptor que aplica `SET LOCAL app.household_id` (RLS, ADR-0003) não
tem dono definido — `sdd-modulo-identity.md` registra isso como "não
decidido aqui" nos Gatilhos de revisão. Decida você mesmo onde ele vive
(sugestão: um pacote técnico `common`/`tenancy`, fora dos 7 módulos de
domínio) e **registre a decisão de volta no SDD correspondente**, do mesmo
jeito que `sdd-modulo-identity.md` já registra "Decisão tomada agora, não
em ADR: quem cria a account WALLET implícita" — é o padrão que este
projeto usa pra nunca deixar suposição silenciosa no código.

## Regra de trabalho

Se encontrar uma decisão que a documentação não cobre, ou uma contradição
entre dois documentos, **pare e pergunte** — não resolva assumindo em
silêncio. Este projeto documentou 21 ADRs uma por uma, cada uma revisada
antes de aceita, exatamente pra evitar isso (ver `CLAUDE.md`). Se
encontrar uma inconsistência entre docs, aponte antes de prosseguir — já
aconteceu de referência cruzada ficar desatualizada depois de uma ADR ser
aceita, e faz parte do processo pegar isso.

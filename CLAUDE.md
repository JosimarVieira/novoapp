# CLAUDE.md

Contexto permanente do projeto. Leia antes de qualquer tarefa.

## O que é

Aplicativo familiar com três domínios — finanças, lista de mercado, tarefas/agenda —
operável por chat (Telegram e, depois, WhatsApp) além da interface web.

Interação por chat é sempre 1:1: cada membro cadastra o próprio número e
conversa com o bot em fio dedicado. Nunca grupo, mesmo onde o canal suportar
— decisão de produto, não limitação técnica. Ver ADR-0008.

O usuário manda `mercado 50` no chat e a despesa é registrada, categorizada,
sem abrir o app. Manda `acabou o arroz` e o item entra na lista da família.

**Diferencial**: não é o parser de despesa (commodity, já existe em Organizze,
Mobills e dezenas de bots). É o **elo entre os três domínios dentro de uma
mesma família**: a esposa marca falta de arroz, o marido compra, lança
`mercado 180`, e o sistema fecha a lista e registra a despesa em um movimento.
Toda decisão de priorização deve favorecer esse elo.

**Modelo de negócio**: SaaS por household (nunca por usuário — ver ADR-0006).
Validação inicial na família do fundador antes de qualquer cliente externo.

## Stack

| Camada   | Escolha                                                                                      |
| -------- | -------------------------------------------------------------------------------------------- |
| Backend  | Java 21 + Quarkus, monolito modular (ADR-0001)                                               |
| Banco    | PostgreSQL, migrations via Flyway                                                            |
| ORM      | JPA/Hibernate                                                                                |
| Frontend | Vue 3 + Vite, entregue como PWA (sem app nativo)                                             |
| LLM      | function calling via LangChain4j (extensão quarkus-langchain4j) — provedor na validação: Mistral AI (ADR-0009) |
| Canais   | Telegram Bot API (primeiro), WhatsApp Cloud API (depois)                                     |

## Regras não negociáveis

Estas não são preferências. Quebrar qualquer uma é bug de severidade máxima.

1. **`household_id` em toda tabela de dado de usuário.** Isolamento aplicado por
   mecanismo automático (filtro Hibernate por interceptor ou RLS no Postgres),
   nunca por disciplina de quem escreve a query. Ver ADR-0003. Exceção
   deliberada: `member` não tem `household_id` — é identidade de pessoa, não
   dado de household; o isolamento dela vive em `household_membership`. Ver
   ADR-0007.
2. **Idempotência por `provider_message_id`** em toda mensagem recebida.
   Telegram e Meta reenviam em timeout. Reentrega virando despesa duplicada
   destrói a confiança do usuário de forma irrecuperável. Ver ADR-0005.
3. **Webhook responde 200 em menos de 3s** e processa assíncrono. A Meta
   desativa webhook que falha de forma recorrente.
4. **O bot nunca acessa repositório direto.** Bot e REST do Vue consomem a
   mesma camada de serviço de domínio.
5. **Nenhum código abaixo de `channel/` sabe de qual canal a mensagem veio.**
6. **Confirmações não gastam LLM.** `sim`, `não`, `1`, `desfazer` são resolvidos
   por curto-circuito determinístico antes de qualquer chamada de modelo.
7. **Todo lançamento é reversível** por `desfazer` no chat e por edição na web.

## Modelo mental do fluxo de mensagem

```
Webhook  ->  Adaptador de canal  ->  InboundMessage (normalizado)
                                          |
                              Resolução de contexto
                    (channel_identity -> member -> household ativo,
                     via household_membership, ADR-0007;
                     + categorias e listas reais da família)
                                          |
                                    Interpretação
                        (LLM com tools declaradas -> Intent + confiança)
                                          |
                              Política de confirmação
                     alta confiança -> executa + recibo curto
                     baixa/ambígua  -> UMA pergunta com opções numeradas
                                          |
                                 Serviço de domínio
```

## Onde está cada coisa

```
docs/00-produto/     visão, personas, glossário (linguagem ubíqua)
docs/01-adr/         decisões arquiteturais, imutáveis após aceitas
docs/02-arquitetura/ SDDs por módulo + modelo de dados
docs/03-specs/       arquivos .feature em Gherkin (fonte de verdade do comportamento)
docs/04-qualidade/   estratégia de testes, definition of done
docs/DECISOES-ABERTAS.md  o que ainda não foi decidido e por quê
ROADMAP.md           etapas com entregável visível
```

## Nome do produto

Em aberto. `novoapp` é placeholder de repositório, não é a marca. Não use em
README, package, material ou resposta de bot. Módulos, tabelas e classes são
neutros em inglês justamente para que a escolha do nome não custe refactor.

## Sobre `legacy/`

Código de um projeto anterior: só finanças, Quarkus + Panache, três frontends.
**Não é referência de arquitetura.** Está sob avaliação de reaproveitamento, e
várias decisões dele foram descartadas nas ADRs. Nunca copie padrão de lá sem
checar contra as ADRs aceitas. Não modifique nada dentro de `legacy/`.

## Como trabalhamos

- **Docs antes de código, mas só até a Etapa 3 do roadmap.** Documentar as
  Etapas 4+ agora produziria ficção: as premissas sobre ambiguidade e
  confirmação só se provam com uso real.
- **Decisão estrutural vira ADR antes de virar código.** Se a implementação
  contradiz uma ADR aceita, escreva uma nova ADR que supera a anterior —
  não edite a antiga.
- **Superar e corrigir são coisas diferentes.** *Superar* é para decisão que
  mudou: ADR nova, a anterior vira `status: superada` com `superada_por`
  preenchido, o conteúdo dela fica intacto como registro histórico.
  *Corrigir* é para registro que nunca foi verdade — edita-se no próprio
  documento, com nota de correção datada no topo e `corrigida_em` no
  frontmatter, sem ADR de superação. Superar registro errado canoniza a
  ficção; corrigir decisão real apaga a história.
- **Decisão que a IA redigiu sem eu ter decidido é erro de registro, não
  decisão.** Ao corrigir, ela sai do documento — não vira alternativa
  descartada com deliberação inventada, nem "falha de processo". Se a ADR
  precisa dessa alternativa para ficar honesta, escreva o motivo real da
  recusa, sem arqueologia. Precedente: ADR-0001 (Spring Boot → Quarkus,
  corrigida em 2026-09-05).
- **Comportamento vira `.feature` antes de virar código.** O Gherkin é a fonte
  de verdade; o teste de aceitação implementa o Gherkin, não o contrário.
- **Dúvida que não é decisão vai para `DECISOES-ABERTAS.md`.** Não invente
  premissa silenciosa.

## Convenções

- Documentação e Gherkin em **português**. Código, nomes de classe, tabelas e
  colunas em **inglês**.
- **Comentário de código em português.** Identificador (classe, método,
  variável, tabela, coluna) é sempre em inglês, sem exceção — nome é
  contrato, não é onde se explica o porquê. Comentário existe pra explicar
  a decisão ou a regra de negócio por trás do código, em português, do
  mesmo jeito que ADR e Gherkin.
- ADR: `docs/01-adr/NNNN-titulo-em-kebab-case.md`, numeração sequencial, nunca
  reutilizada.
- Termos de domínio seguem `docs/00-produto/glossario.md`. Se um termo não
  está lá, adicione antes de usar no código.

## Como me ajudar melhor

Ao propor algo: aponte o furo antes do elogio. Prefiro corrigir uma proposta
concreta a responder um questionário. Se a informação estiver faltando, faça
no máximo uma pergunta e siga assumindo o resto — declarando a suposição.

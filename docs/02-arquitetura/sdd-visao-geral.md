---
tipo: sdd
modulo: geral
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0005
  - ADR-0007
  - ADR-0018
  - ADR-0020
  - ADR-0021
  - ADR-0022
---

# SDD — Visão geral

## Responsabilidade

Transformar mensagem de chat em ação de domínio de uma família, e expor as
mesmas ações por interface web.

## Módulos e fronteiras

| Módulo | Faz | Não faz |
|---|---|---|
| `channel` | Recebe webhook, normaliza para `InboundMessage`, persiste com idempotência, envia resposta pelo canal de origem | Não interpreta. Não conhece domínio. |
| `identity` | Resolve `ChannelIdentity` → `Member` → household ativo, via `HouseholdMembership` ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)). Define o contexto de tenant. Cria household self-service e resolve convite ([ADR-0020](../01-adr/0020-convite-de-membro.md)) quando a identidade não resolve. Autentica sessão web por e-mail e senha, canal `WEB` ([ADR-0021](../01-adr/0021-autenticacao-web.md)). Expõe `OutboundMessagePort` (interface), implementado por `channel`. | Não verifica posse do e-mail nem recupera senha (negativas em aberto da ADR-0021, resolver antes da Etapa 6). Não formata recibo de domínio (isso é `conversation`). |
| `nlu` | Monta contexto do household (lê categoria de `finance`), chama LLM com tools, devolve `Intent` + confiança | Não executa nada. Não persiste dado de domínio — nem em `finance`, que só lê. |
| `conversation` | Política de confiança, `PendingAction`, curto-circuito de confirmação, formatação de recibo | Não decide regra de negócio de domínio. |
| `finance` | Lançamentos, categorias, contas, estorno | Não fala com canal. |
| `shopping` | Lista, itens, fechamento de compra | Cria lançamento **através** de `finance`, nunca escrevendo em `transaction`. |
| `tasks` | Tarefas | — |

Fora da tabela, porque não é módulo de domínio: `common/tenancy` aplica o
isolamento multi-tenant na conexão ([ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md),
[`sdd-modulo-tenancy.md`](sdd-modulo-tenancy.md)). Todo módulo depende dele; ele não depende de
nenhum. `common/message` guarda o `InboundMessage` normalizado — o tipo que
atravessa a fronteira de `channel` sem carregar de qual canal veio.

## Regra de dependência

```
channel        ->  identity
channel        ->  conversation  ->  nlu
conversation   ->  identity
nlu            ->  finance          -- so leitura de categoria, nunca escrita
finance / shopping / tasks  ->  identity
```

`finance`, `shopping` e `tasks` **não podem** importar `channel` nem `nlu`.
Barrado por teste ArchUnit, não por revisão de código — onze regras em
`ModuleBoundariesTest`, incluindo ausência de ciclos entre os módulos e a regra
não negociável 5 (nenhum tipo com nome de provedor fora de `channel`).

`shopping` pode depender de `finance` (o elo é dirigido nesse sentido).
`finance` não pode depender de `shopping`.

`channel` depende de `identity` por dois motivos: resolver o contexto de
tenant logo após normalizar a mensagem (regra 5 do CLAUDE.md — nada abaixo
de `channel` sabe de qual canal veio, mas `channel` mesmo precisa saber
"de quem" antes de repassar), e disparar o onboarding determinístico
([ADR-0020](../01-adr/0020-convite-de-membro.md)) quando a identidade não resolve. `conversation` também depende
de `identity` — precisa do `OutboundMessagePort` (ver `sdd-modulo-identity.md`)
pra enviar recibo e perguntas, e do nome do membro/household pra formatar
texto. Nenhuma dessas arestas estava desenhada aqui antes — ficaram implícitas até
a Etapa 1 forçar a decisão, uma de cada vez, à medida que cada SDD de
módulo foi escrito (`sdd-modulo-channel.md`, `sdd-modulo-identity.md`,
`sdd-modulo-nlu.md`). `nlu` depende de `finance` só pra montar o enum de
categorias da tool de function calling — nunca escreve em tabela de
`finance`.

## Fluxo de referência: fechamento de compra

O caminho mais importante do produto.

1. Usuário: `comprei tudo, 180`
2. `channel` persiste, responde 200, publica evento interno
3. `identity` resolve household
4. `nlu` recebe contexto (lista ativa com N itens pendentes, categorias) e
   escolhe `fecharCompra(valor=18000, itens=todos)`
5. `conversation` avalia confiança. Alta → executa
6. `shopping.checkout()` abre transação única:
   - marca itens pendentes como `PURCHASED`
   - chama `finance.registrarDespesa()` com categoria inferida
   - grava `list_checkout` ligando os dois
7. Recibo: `✅ 7 itens marcados · Mercado R$ 180,00 · responder desfazer`

Caminho de erro em cada passo:
- (4) nenhuma lista ativa → pergunta se é despesa avulsa
- (5) confiança média → lista os 7 itens e pede confirmação numerada
- (6) falha em `finance` → rollback total. Não existe estado de lista fechada
  sem lançamento. Recibo de erro no chat, não só no log.

## Falhas transversais

| Falha | Comportamento |
|---|---|
| LLM indisponível ou timeout | Mensagem fica `RECEIVED`, retry com backoff, e aviso no chat após a segunda falha. Nunca adivinhar. |
| Reentrega de webhook | Descarte silencioso ([ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md)) |
| Identidade desconhecida | Sem convite pendente pro telefone → oferece criar household nova, só cria após confirmação explícita. Com convite pendente pro telefone → pede compartilhar contato pra aceitar. Nunca cria household nem aceita convite sem confirmação do usuário. Fluxo determinístico, sem LLM — ver [ADR-0020](../01-adr/0020-convite-de-membro.md) e `vinculo-de-identidade.feature`. |
| `PendingAction` expirada no chat | Não é descartada: informa que expirou no chat e repete a pergunta ali; a mesma pendência também passa a aparecer na central de pendências do app (Etapa 4), com notificação, até ser resolvida — [ADR-0018](../01-adr/0018-central-de-pendencias.md) |
| Erro após 200 do webhook | Recibo de erro no chat. O usuário nunca fica sem resposta. |

## Pontos em aberto

Ver [`docs/DECISOES-ABERTAS.md`](../DECISOES-ABERTAS.md).

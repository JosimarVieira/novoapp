---
tipo: sdd
modulo: geral
status: escrito
atualizado_em: 2026-09-08
adrs:
  - ADR-0001
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
| `nlu` | Monta contexto do household (lê categoria de `finance` e itens pendentes de `shopping`), chama LLM com tools, devolve `Intent` + confiança | Não executa nada. Não persiste dado de domínio — nem em `finance` nem em `shopping`, que só lê. Não pergunta nada: decidir o que fazer com uma confiança média é de `conversation`. |
| `conversation` | Política de confiança, `PendingAction`, curto-circuito de confirmação, formatação de recibo | Não decide regra de negócio de domínio. |
| `finance` | Lançamentos, categorias, contas, estorno | Não fala com canal. |
| `shopping` | Lista, itens, fechamento de compra ([`sdd-modulo-shopping.md`](sdd-modulo-shopping.md)) | Cria lançamento **através** de `finance`, nunca escrevendo em `transaction`. Não pergunta nada e não resolve nome de membro. |
| `tasks` | Tarefas | — |

Fora da tabela, porque não é módulo de domínio: `common/tenancy` aplica o
isolamento multi-tenant na conexão ([ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md),
[`sdd-modulo-tenancy.md`](sdd-modulo-tenancy.md)). Todo módulo depende dele; ele não depende de
nenhum. `common/message` guarda o `InboundMessage` normalizado — o tipo que
atravessa a fronteira de `channel` sem carregar de qual canal veio.

## Regra de dependência

É a [ADR-0001](../01-adr/0001-monolito-modular-em-quarkus.md) operacionalizada:
um deploy só, "organizado em pacotes com fronteira explícita", com comunicação
entre módulos apenas por interface pública de serviço. Sem esta seção e sem o
teste que a trava, "modular" seria adjetivo de intenção.

```
channel        ->  identity
channel        ->  conversation  ->  nlu
conversation   ->  identity
conversation   ->  finance, shopping   -- executa o que a interpretacao decidiu
nlu            ->  finance, shopping   -- so leitura, pra montar o contexto do modelo
finance / shopping / tasks  ->  identity
```

`finance`, `shopping` e `tasks` **não podem** importar `channel` nem `nlu`.
Barrado por teste ArchUnit, não por revisão de código — onze regras em
`ModuleBoundariesTest`, incluindo ausência de ciclos entre os módulos e a regra
não negociável 5 (nenhum tipo com nome de provedor fora de `channel`).

As duas arestas para `shopping` entraram na Etapa 2a, quando o módulo passou a
existir de fato. A de `conversation` é execução (adicionar item, marcar
comprado); a de `nlu` é leitura pura dos itens pendentes, pelo mesmo motivo que a
de `finance`: sem eles no contexto, o modelo não casa "comprei o arroz" com o
item "Arroz" da lista.

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
`sdd-modulo-nlu.md`). `nlu` depende de `finance` e de `shopping` só pra montar o contexto do modelo
(categorias e itens da lista, [ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)) —
nunca escreve em tabela de nenhum dos dois.

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
| `PendingAction` expirada no chat | Não é descartada: informa que expirou no chat e repete a pergunta ali; a mesma pendência também passa a aparecer na central de pendências do app (Etapa 4), com notificação, até ser resolvida — [ADR-0018](../01-adr/0018-central-de-pendencias.md). Duas saídas deliberadas: `desfazer` volta a significar estorno (a [ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md) dá precedência só a pendência não expirada) e mensagem que não é atalho segue como mensagem nova, para uma pendência esquecida não travar a conversa. Detalhado em `sdd-modulo-conversation.md`. |
| Erro após 200 do webhook | Recibo de erro no chat. O usuário nunca fica sem resposta. |

## Pontos em aberto

Ver [`docs/DECISOES-ABERTAS.md`](../DECISOES-ABERTAS.md).

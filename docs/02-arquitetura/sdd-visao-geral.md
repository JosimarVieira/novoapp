# SDD — Visão geral

## Responsabilidade

Transformar mensagem de chat em ação de domínio de uma família, e expor as
mesmas ações por interface web.

## Módulos e fronteiras

| Módulo | Faz | Não faz |
|---|---|---|
| `channel` | Recebe webhook, normaliza para `InboundMessage`, persiste com idempotência, envia resposta pelo canal de origem | Não interpreta. Não conhece domínio. |
| `identity` | Resolve `ChannelIdentity` → `Member` → `Household`. Define o contexto de tenant. | Não autentica web (isso é `auth`, fora do escopo até Etapa 6). |
| `nlu` | Monta contexto do household, chama LLM com tools, devolve `Intent` + confiança | Não executa nada. Não persiste dado de domínio. |
| `conversation` | Política de confiança, `PendingAction`, curto-circuito de confirmação, formatação de recibo | Não decide regra de negócio de domínio. |
| `finance` | Lançamentos, categorias, contas, estorno | Não fala com canal. |
| `shopping` | Lista, itens, fechamento de compra | Cria lançamento **através** de `finance`, nunca escrevendo em `transaction`. |
| `tasks` | Tarefas | — |

## Regra de dependência

```
channel  ->  conversation  ->  nlu
                  |
                  v
   finance / shopping / tasks   ->  identity
```

`finance`, `shopping` e `tasks` **não podem** importar `channel` nem `nlu`.
Barrado por teste ArchUnit, não por revisão de código.

`shopping` pode depender de `finance` (o elo é dirigido nesse sentido).
`finance` não pode depender de `shopping`.

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
| Reentrega de webhook | Descarte silencioso (ADR-0005) |
| Identidade desconhecida | Responde convite de vínculo. Não cria household automaticamente. |
| `PendingAction` expirada | Responde que expirou e repete a pergunta original |
| Erro após 200 do webhook | Recibo de erro no chat. O usuário nunca fica sem resposta. |

## Pontos em aberto

Ver `docs/DECISOES-ABERTAS.md`.

# Índice das especificações

O Gherkin em [`features/`](features/) é a fonte de verdade do comportamento
(`CLAUDE.md`): o teste de aceitação implementa o cenário, nunca o contrário.

Esta nota existe por uma limitação de ferramenta, não por gosto de organização.
O Obsidian indexa `.md`, canvas e bases — `.feature` fica fora da busca global,
do grafo e dos backlinks mesmo com "detectar todas as extensões" ligado. Sem
este índice, o documento mais autoritativo do projeto é o único invisível na
ferramenta usada pra navegar nele.

**Ela não repete cenário.** Texto de cenário vive num lugar só, no `.feature`.
Aqui ficam só o mapa e o estado.

## As features

| Feature | Cobre | Cenários | Etapa |
|---|---|---|---|
| [`financas-lancamento-por-chat`](features/financas-lancamento-por-chat.feature) | Despesa por mensagem curta: categoria reconhecida, variações de escrita, categoria inexistente, ambiguidade, valor ausente, descrição, desfazer, reentrega, número não vinculado | 11 | 4 `@etapa1`, 7 `@etapa2` |
| [`mercado-lista-de-compras`](features/mercado-lista-de-compras.feature) | Lista compartilhada: adicionar por linguagem natural, quantidade, item repetido, "o que está faltando?", marcar comprado, reentrega | 9 | sem tag |
| [`elo-fechamento-de-compra`](features/elo-fechamento-de-compra.feature) | O elo lista→despesa: fechar tudo ou em partes, falha atômica, desfazer dos dois lados, fechar sem lista ativa, reentrega | 9 | sem tag |
| [`vinculo-de-identidade`](features/vinculo-de-identidade.feature) | Onboarding: primeiro contato cria a própria família; convite entra em família existente; telefone errado, convite expirado, convite reusado, pessoa em duas famílias | 10 | 9 `@etapa1`, 1 `@etapa2` |

**Não escrita**: tarefas/agenda. Fica pra quando a etapa dela chegar.

`elo-fechamento-de-compra` é a que materializa o diferencial do produto. O
comentário no topo do arquivo diz o que isso implica: cenário cortado ali por
escopo tira a razão de existir do produto.

## Sobre as tags

`@etapa1` e `@etapa2` marcam o que cada etapa do ROADMAP entrega, e é por elas
que `Etapa1AcceptanceTest` e `Etapa2AcceptanceTest` selecionam o que rodar.

Só `financas-lancamento-por-chat` e `vinculo-de-identidade` têm tags — as
features de mercado e do elo não têm nenhuma. Não é esquecimento sem
consequência: enquanto ficarem assim, os cenários delas não são selecionáveis
por etapa, e a Etapa 2 vai precisar decidir isso antes de tirar o `@Disabled`
do `Etapa2AcceptanceTest`. Registrado aqui como observação, não como decisão.

## Onde ler o resto

- Por que o comportamento é esse: [`../01-adr/`](../01-adr/) (índice em
  [`../adr.base`](../adr.base))
- Como os cenários viram teste: [`../04-qualidade/estrategia-de-testes.md`](../04-qualidade/estrategia-de-testes.md)
- Em que ordem: [`../../ROADMAP.md`](../../ROADMAP.md)

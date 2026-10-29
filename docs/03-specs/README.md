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
| [`financas-lancamento-por-chat`](features/financas-lancamento-por-chat.feature) | Despesa por mensagem curta: categoria reconhecida, variações de escrita, categoria inexistente, ambiguidade, valor ausente, descrição, desfazer, reentrega, número não vinculado, mais o que o saneamento e o uso real acrescentaram (confiança média, mensagem nova sobre pendência aberta, acento na categoria-pai, valor inventado, resposta pelo nome da opção) | 26 | 4 `@etapa1`, 12 `@etapa2`, 10 `@saneamento` |
| [`mercado-lista-de-compras`](features/mercado-lista-de-compras.feature) | Lista compartilhada: adicionar por linguagem natural, quantidade, item repetido, "o que está faltando?", marcar comprado, item escrito sem acento, reentrega, remover item, e vários itens numa mensagem só ao marcar e ao remover | 18 | 9 `@etapa2`, 3 `@etapa3`, 6 `@saneamento` |
| [`elo-fechamento-de-compra`](features/elo-fechamento-de-compra.feature) | O elo lista→despesa: fechar tudo ou em partes, falha atômica, desfazer dos dois lados, fechar sem lista ativa, reentrega, item com valor na mesma mensagem (`açúcar 20`) | 11 | 11 `@etapa3` |
| [`mercado-botoes-na-lista`](features/mercado-botoes-na-lista.feature) | Botões na lista ([ADR-0040](../01-adr/0040-botoes-na-lista-de-compras-e-carrinho-por-fio.md)): remover e devolver, carrinho do fio, fechar pelo carrinho, categoria do último fechamento, clique em lista desatualizada, toque duplo, reentrega do clique | 16 | 16 `@acoes-no-chat` |
| [`vinculo-de-identidade`](features/vinculo-de-identidade.feature) | Onboarding: primeiro contato cria a própria família; convite entra em família existente; telefone errado, convite expirado, convite reusado, pessoa em duas famílias, convite com confiança média | 11 | 9 `@etapa1`, 1 `@etapa2`, 1 `@saneamento` |

**Não escrita**: tarefas/agenda. É o primeiro trabalho da Etapa 2b, que
começa justamente por escrevê-la.

`elo-fechamento-de-compra` é a que materializa o diferencial do produto. O
comentário no topo do arquivo diz o que isso implica: cenário cortado ali por
escopo tira a razão de existir do produto.

## Sobre as tags

As tags marcam a qual etapa do ROADMAP cada cenário pertence, e é por elas que
os testes de aceitação selecionam o que rodar.

Onde a tag fica depende do arquivo. `financas-lancamento-por-chat` e
`vinculo-de-identidade` são mistos, então cada cenário carrega a própria tag.
`elo-fechamento-de-compra` é inteiro de uma etapa só, então a tag está na linha
`Funcionalidade:` e é herdada — uma linha em vez de nove repetidas.
`mercado-lista-de-compras` também era, e deixou de ser: a tag `@etapa2` continua
na `Funcionalidade:` e é herdada por todo cenário novo, inclusive pelos que não
são da Etapa 2a.

`@acoes-no-chat` é a quinta, criada em 2026-10-29 com a
[ADR-0040](../01-adr/0040-botoes-na-lista-de-compras-e-carrinho-por-fio.md). Também
não é etapa — a mudança não abre domínio novo — e mora num arquivo próprio
justamente para não herdar o `@etapa2` da `Funcionalidade:` de
`mercado-lista-de-compras`. Roda em `AcoesNoChatAcceptanceTest`, que nasceu
desabilitado.

`@saneamento` é a quarta tag, e não é etapa. Marca cenário nascido de auditoria
ou de uso real depois de a etapa dele já estar fechada
([`PLANO-SANEAMENTO-PRE-ETAPA-3.md`](../../PLANO-SANEAMENTO-PRE-ETAPA-3.md), e o
que veio depois dele). Existe pelo mesmo motivo que `@etapa2` teve runner
próprio: escopo novo não pode pintar de vermelho o portão de uma etapa fechada.
Roda em `SaneamentoAcceptanceTest`, e é por causa dela que `Etapa2AcceptanceTest`
seleciona `@etapa2 and not @saneamento` — em `mercado-lista-de-compras` a tag da
`Funcionalidade:` alcançaria o cenário de acentuação sem isso.

Nenhum cenário do elo é destacável para a Etapa 2: todos dependem de
`fecharCompra` atômico, e o cenário de falha ("não deixa a lista fechada") só
faz sentido se a atomicidade existir. Fatiar o elo entregaria a metade que não
prova nada.

`@etapa3` só passa a rodar quando o `Etapa3AcceptanceTest` sair do `@Disabled` —
a tag sozinha não dá cobertura, apenas torna os cenários selecionáveis. A classe
existe desde 2026-09-19, desabilitada de propósito: escopo que falta tem de ser
visível na própria suíte.

> **Correção de registro — 2026-09-19.** As contagens da tabela acima estavam
> paradas em 2026-09-07 (11, 9, 9 e 10 cenários) e não continham a tag
> `@saneamento`, criada em 2026-09-16. Recontadas a partir dos próprios
> arquivos. É registro que deixou de ser verdade, corrigido no próprio
> documento (`CLAUDE.md`, "Superar e corrigir são coisas diferentes").
>
> **Correção de registro — 2026-10-24.** De novo: a Etapa 3 acrescentou dois
> cenários ao elo ([ADR-0038](../01-adr/0038-item-com-valor-e-fechamento-parcial.md))
> e três `@etapa3` a `mercado-lista-de-compras`
> ([ADR-0039](../01-adr/0039-remover-item-da-lista.md)), e a tabela continuou
> em 9 e 10. Recontadas a partir dos arquivos, já com os cinco `@saneamento`
> de vários itens numa mensagem, abertos no mesmo dia. O parágrafo sobre o
> `@Disabled` do `Etapa3AcceptanceTest`, logo acima, também deixou de ser
> verdade em 2026-09-21, quando a Etapa 3 fechou e a suíte passou a integrar o
> portão do CI.

## Onde ler o resto

- Por que o comportamento é esse: [`../01-adr/`](../01-adr/) (índice em
  [`../adr.base`](../adr.base))
- Como os cenários viram teste: [`../04-qualidade/estrategia-de-testes.md`](../04-qualidade/estrategia-de-testes.md)
- Em que ordem: [`../../ROADMAP.md`](../../ROADMAP.md)

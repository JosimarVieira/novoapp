---
tipo: sdd
modulo: conversation
status: escrito
atualizado_em: 2026-09-18
adrs:
  - ADR-0004
  - ADR-0015
  - ADR-0018
  - ADR-0023
  - ADR-0024
  - ADR-0025
  - ADR-0026
  - ADR-0029
  - ADR-0031
  - ADR-0033
---

# SDD — Módulo `conversation`

## Responsabilidade

Política de confiança, `PendingAction`, curto-circuito de confirmação,
formatação de recibo ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)). Não decide regra de negócio de domínio —
só orquestra `nlu` → confiança → módulo de domínio → recibo.

## Escopo desta versão (Etapa 2a)

A Etapa 1 cobria só confiança alta, decisão imediata, sem pergunta nenhuma no
meio. A Etapa 2a fechou o resto da tabela da
[ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md):
`PendingAction`, curto-circuito de confirmação, confiança média/baixa, e o
`desfazer` com a precedência da
[ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md). É o módulo que mais
cresceu na etapa, como o gatilho de revisão anterior previa.

**Não cobre ainda**: `fecharCompra` e o elo lista → despesa (Etapa 3); tarefas
(Etapa 2b); a central de pendências na web (Etapa 4 — o mecanismo está aqui, a
tela não).

## Depende de

- `nlu` — interpretação, inclusive a da mensagem que chega com pergunta em
  aberto no fio ([ADR-0029](../01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)) — que era uma segunda chamada da correção livre de
  categoria ([ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)).
- `finance` — registrar despesa, criar categoria confirmada, estornar.
- `shopping` — adicionar item, marcar comprado, consultar a lista. Aresta nova
  da Etapa 2a; a regra de dependência do `sdd-visao-geral.md` foi atualizada
  junto com o teste ArchUnit que a trava.
- `identity` — nome do membro/household pra formatar o recibo,
  `OutboundMessagePort` pra enviar, `MemberDirectory` pra traduzir
  `member_id` em nome ("pedido por Ana"), e `InviteIssuer` pra emitir convite
  ([ADR-0020](../01-adr/0020-convite-de-membro.md)).

## Estrutura interna proposta

```
conversation/
  ConversationOrchestrator  -- process(InboundMessage, ResolvedContext, canal, externalId)
  ConfidencePolicy          -- as tres faixas da ADR-0004, com os limiares vindos de config
  PendingActionService      -- abre, encontra e resolve pendencia (o mecanismo generico)
  PendingIntent             -- o conteudo de intent_json: o tipo da pergunta e,
                               desde a ADR-0029, a acao adiada (deferred)
  PendingActionType         -- a pergunta: CREATE_CATEGORY | CHOOSE_CATEGORY |
                               ASK_AMOUNT | CONFIRM_PURCHASE | CONFIRM_INTENT
  PendingIntent.DeferredAction -- a acao: REGISTER_EXPENSE | ADD_LIST_ITEMS |
                               MARK_PURCHASED | ADD_ALREADY_PURCHASED | INVITE_MEMBER
  ShortCircuit              -- sim / nao / desfazer / numero, sem LLM (regra 6)
  ReceiptFormatter          -- todo texto que este modulo manda pro chat
  ProcessingOutcome         -- o que aconteceu, devolvido pra channel registrar
  entity/     PendingAction, PendingResolution
  repository/ PendingActionRepository
```

Três coisas decididas em 2026-09-05, ao implementar a Etapa 1:

**O desfecho é devolvido, não escrito.** `inbound_message.status` é de `channel`,
e `conversation` não pode importar `channel` (sdd-visao-geral.md). Então
`process()` devolve um `ProcessingOutcome(resultado, confiança, intenção)` e é
`channel` quem grava.

**`process`, não `processar`.** Identificador em inglês é regra sem exceção no
CLAUDE.md. Mesma correção feita em `sdd-modulo-finance.md`.

**O orquestrador não é transacional.** A chamada ao LLM tem cauda de latência
imprevisível ([ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md)) e não pode segurar conexão de banco aberta. Cada
passo — ler categorias, gravar lançamento, atualizar o log — abre a própria
transação curta.

Cinco decididas em 2026-09-07, ao implementar a Etapa 2a.

### O tipo da pendência vive dentro de `intent_json`, no campo `type`

A [ADR-0018](../01-adr/0018-central-de-pendencias.md) desenha `PendingAction`
como mecanismo genérico: qualquer pendência resolve por `sim`/`não`/número via
curto-circuito. A [ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)
abre uma exceção para **um** tipo: na pergunta de criação de categoria, uma
resposta que não é nenhum desses atalhos vira correção livre e gasta uma segunda
chamada ao modelo. Logo o código precisa saber qual tipo está aberto antes de
escolher entre o "não entendi" genérico e a tool de correção — e o schema de
`pending_action` não tem discriminador.

**Decisão**: o discriminador é o campo `type` dentro de `intent_json`, lido para
o enum `PendingActionType`. As duas alternativas foram descartadas por motivos
diferentes:

- **coluna nova** contrariaria a ADR-0018, que decide explicitamente que a
  central de pendências da Etapa 4 se sustenta *sem* mudança de schema. Abrir
  exceção a isso na primeira etapa que usa a tabela esvaziaria a decisão;
- **deduzir do formato de `options_json`** amarraria a regra de resolução a uma
  escolha de apresentação. Uma pendência de sim/não e uma de valor ausente têm
  as duas `options_json` vazio e se comportam de forma diferente — o formato das
  opções simplesmente não carrega essa informação.

`intent_json` já é, por definição, "o que gerou a pergunta". O tipo dela é parte
disso.

### `intent_json` de `pending_action` é estado interno, não registro do que foi dito ao modelo

Os campos dele têm nomes em inglês, diferente do `intent_json` de
`inbound_message`, que espelha em português o vocabulário da tool. Não é
inconsistência: aquele é registro forense do que foi enviado ao LLM, e é lido na
calibração da Etapa 5; este nunca sai de `conversation`.

### A pendência pertence à conversa, não ao household

`findOpen` filtra por `channel_identity_id`. A
[ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md) diz "para o
household **ou** membro que enviou `desfazer`", e os dois não são a mesma coisa:
se Ana tem pergunta aberta e Bruno manda `desfazer`, o dele cancelaria uma
pergunta que ele nunca viu. Chat é sempre 1:1, em fio dedicado por membro
([ADR-0008](../01-adr/0008-interacao-1-1-por-membro-nunca-em-grupo.md)) — a
pergunta foi feita numa conversa e é nela que se resolve.

O estorno continua household-wide, de qualquer membro
([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md)), como a
ADR-0025 decidiu. As duas coisas convivem: o `desfazer` de Bruno não toca a
pergunta da Ana e estorna o último lançamento da família, mesmo que tenha sido
Ana quem lançou.

### `desfazer` fora do prazo volta a ser estorno

Colisão entre ADRs que só aparece na implementação: a ADR-0018 manda responder
"expirou, veja no aplicativo" a toda resposta curta fora do TTL, e a ADR-0025 dá
precedência sobre o estorno só à pendência **não expirada**. Prevalece a
ADR-0025 para `desfazer`, e a ADR-0018 para `sim`/`não`/número. Mensagem que não
é atalho nenhum segue como mensagem nova — caso contrário uma pendência que
ninguém respondeu travaria a conversa indefinidamente, que é o oposto do que a
ADR-0018 quer.

### Nenhum texto deste módulo é literal no código

Corrigido em 2026-09-08, depois da auditoria: a
[ADR-0015](../01-adr/0015-internacionalizacao.md) exige isso desde a Etapa 1 e
estava sendo contrariada. `ReceiptFormatter` monta a **estrutura** da resposta;
o conteúdo vem de `messages_pt_BR.properties`, pelo idioma do membro
(`ResolvedContext.locale`, que vem de `member.preferred_locale`).

`Reply`, a classe interna que sabe para onde responder, passou a carregar também
o idioma. Não é conveniência: a ADR-0015 registra como consequência negativa que
esquecer de propagar o locale "gera resposta no idioma errado, silenciosamente".
Carregá-lo junto do endereço de resposta é o que torna difícil esquecer.

Dinheiro e data continuam em pt-BR mesmo quando o texto sair em outro idioma —
a ADR-0015 põe moeda explicitamente fora de escopo.

### Os limiares de confiança e o TTL são config, com valor provisório declarado

`novoapp.conversation.confidence.high` (0,8),
`novoapp.conversation.confidence.low` (0,4) e
`novoapp.conversation.pending-action.ttl` (10 min) vivem em
`application.properties`, com comentário dizendo que são palpite. Global do app,
nunca por household ([decisões abertas #7 e #8](../DECISOES-ABERTAS.md)) e nunca
constante escondida no código. Os números de verdade saem da Etapa 5.

## Fluxo

**Pergunta em aberto vem antes de qualquer interpretação.** É a decisão central
do módulo, e não um detalhe de ordem: é o que faz o curto-circuito da regra 6
valer (responder `sim` não pode gastar chamada de modelo) e o que dá ao
`desfazer` a precedência absoluta sobre estorno que a
[ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md) decidiu.

1. `channel` já resolveu o contexto (via `identity`) e repassa
   `InboundMessage` + `ResolvedContext`.
2. `PendingActionService.findOpen` procura pergunta em aberto **desta
   conversa** (`channel_identity_id`, não household — ver abaixo).
3. Havendo pendência **dentro do prazo**, `ShortCircuit` classifica a resposta:
   - `desfazer` → rejeita a pendência e responde "cancelei a pergunta",
     dizendo explicitamente que **não** houve estorno;
   - `não` → rejeita, sem gravar nada;
   - `sim` → executa o que a pendência guardava (criar categoria e lançar;
     registrar o item como comprado);
   - número → escolhe a opção, quando a pendência tem opções;
   - qualquer outra coisa → repete a pergunta, **exceto** na pendência de
     criação de categoria, onde vira correção livre e gasta uma segunda
     chamada ao modelo ([ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md),
     exceção explícita à regra 6).
4. Havendo pendência **fora do prazo**, o atalho não existe mais
   ([ADR-0018](../01-adr/0018-central-de-pendencias.md)): o bot avisa que
   expirou, aponta para o aplicativo e repete a pergunta. A pendência continua
   em aberto — `resolution` fica nula, nada é descartado. Duas saídas
   deliberadas dessa regra: `desfazer` volta a significar estorno (a ADR-0025 dá
   precedência só a pendência *não* expirada), e mensagem que não é atalho
   nenhum segue como mensagem nova — sem isso, uma pendência esquecida travaria
   a conversa para sempre.
5. Sem pendência, `desfazer` estorna (ADR-0025) e qualquer outra coisa vai para
   `nlu.interpret`.
6. `ConfidencePolicy` classifica a confiança devolvida:
   - **alta** → executa e responde recibo;
   - **média** → UMA pergunta: as opções numeradas que `nlu` montou quando há
     mais de uma, ou a intenção ecoada de volta em `sim`/`não` quando há uma só.
     Vale para **toda intenção que escreve**, e não só para despesa entre
     categorias parecidas ([ADR-0029](../01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md));
   - **baixa** → pergunta aberta curta, sem adivinhar.
7. Dois casos escapam da faixa de confiança, de propósito:
   - **categoria inexistente** ([ADR-0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md))
     sempre pergunta — **nos dois sentidos da faixa**. Com confiança alta,
     porque não é escolher entre alternativas existentes, é confirmar um nome
     novo, e criar categoria é irreversível pelo chat. Com confiança baixa,
     porque perguntar aqui não é adivinhar: nada é criado sem o `sim`, e uma
     pergunta com um nome concreto dentro custa uma palavra para responder,
     enquanto "não entendi" custa reescrever a mensagem e não ensina nada.

     **Corrigido em 2026-09-17**, com dado de uso real. O código verificava a
     faixa baixa antes, e `Pet shop 80` — mensagem sem nenhuma ambiguidade —
     chegava do Mistral com `confianca` 0,3. O bot respondia "não entendi essa"
     sabendo exatamente o que a pessoa quis, e com o nome da categoria na mão.
     É colisão entre ADRs aceitas (a [ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
     manda confiança baixa não adivinhar), resolvida a favor da ADR-0024 e
     registrada aqui, no molde das três colisões que a Etapa 2a resolveu.

     Fica o dado para a [decisão aberta #7](../DECISOES-ABERTAS.md): o modelo
     parece reportar confiança baixa sistematicamente quando precisa sugerir
     categoria nova. Se isso se confirmar na Etapa 5, o limiar é que está
     errado, não o caso;
   - **valor ausente** sempre pergunta, porque não há valor a adivinhar —
     **também na faixa baixa**, desde a
     [ADR-0033](../01-adr/0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md).

     Esta frase estava aqui desde a Etapa 2a e o código não a cumpria: a faixa
     baixa vinha antes e devolvia "não entendi". Em uso real, em 2026-09-18,
     `petshop` sozinho — categoria que existe, `categoryId` resolvido, só o
     valor faltando — recebeu "não entendi essa" minutos depois de o mesmo bot
     ter perguntado "Quanto foi em Ração?". Design escrito sem ADR que o
     sustente é design que ninguém tem como cobrar; a ADR-0033 é o lastro, e
     fixa a ordem dos passos, não só a frase.
   - E a **descrição** ([ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md))
     nunca pergunta nada: ausência dela não reduz confiança e não impede
     execução.
   - A terceira, acrescentada pela ADR-0029: **consultar a lista** executa em
     qualquer confiança acima da baixa. Não há o que desfazer numa leitura, e
     perguntar antes de ler é fricção sem risco do outro lado. É a única
     intenção fora da regra, e ela não escreve nada.
8. `ReceiptFormatter` monta o texto e `OutboundMessagePort` envia. O recibo ecoa
   a descrição quando ela existe (ADR-0023) e a hierarquia quando a categoria
   tem pai (ADR-0026). O household é nomeado **só** quando a pessoa tem mais de
   um vínculo, que é o que a [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)
   exige para tornar erro de contexto visível — e o que ela proíbe de aparecer
   para quem tem uma família só.

## O recibo não nomeia a conta (decidido em 2026-09-05)

A [ADR-0019](../01-adr/0019-conta-padrao-por-membro.md) afirma de passagem, ao listar as mitigações dela, que "recibo
sempre nomear a conta usada" seria "regra existente". Essa regra não existe em
nenhum outro documento: nem no glossário (verbete `Recibo`), nem no
`financas-lancamento-por-chat.feature`, nem neste SDD. O recibo da Etapa 1 segue
o Gherkin — valor, categoria e como desfazer — e não nomeia conta: nesta etapa só
existe uma conta por household, e `default_account_id` não tem como ser
preenchido antes da Etapa 4, então nomear só acrescentaria ruído sem tornar
nenhum erro visível.

Quando houver mais de uma conta escolhível, isso vira decisão explícita — e
provavelmente uma correção de registro na ADR-0019.

## Erros

- Falha em qualquer serviço de domínio → recibo de erro no chat, nunca silêncio
  (regra geral do `sdd-visao-geral.md`).
- Correção livre que o modelo não conseguiu ler → repete a pergunta em vez de
  descartar a pendência. Nada se perde por o modelo ter falhado.

## Testes

- ArchUnit: `conversation` não importa `channel` nem `tasks` (passou a poder
  importar `shopping`, que é o que ele executa).
- Cenários `@etapa1` e `@etapa2` de `financas-lancamento-por-chat.feature`, os
  nove de `mercado-lista-de-compras.feature` e o de emissão de convite em
  `vinculo-de-identidade.feature`.
- Isolamento de tenant cobrindo `pending_action`: é a tabela que guarda texto de
  conversa, e vazamento nela não teria conserto.
- `MessageBundleTest` quebra o build se aparecer texto literal em
  `ReceiptFormatter` — é a regra da ADR-0015 verificada, e não confiada à
  revisão.

## A pendência guarda o que executar (decidido em 2026-09-16)

O gatilho de revisão da Etapa 3 abaixo mandava verificar se "fechar compra sem
informar valor" caberia no mecanismo existente. **Não cabia**: `ASK_AMOUNT` era
genérico no nome e específico na execução — quando a resposta chegava, o
orquestrador chamava `registrarDespesa` direto e lia a primeira opção guardada
como id de categoria.

A [ADR-0029](../01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)
separou as duas coisas que estavam no mesmo campo:

- `PendingActionType` descreve **a pergunta** — o que falta saber;
- `PendingIntent.deferred` descreve **a ação** — o que fazer quando souber.

`executeDeferred` é um `switch` sobre a segunda, e não sobre a primeira.
Acrescentar `fecharCompra` na Etapa 3 passa a ser um valor no enum e um caso ali
— sem tipo de pendência especial, que era o que o gatilho pedia.

O tipo `CONFIRM_INTENT` entrou junto: é a pendência da confiança média, a que
guarda a intenção inteira esperando um `sim`. `CONFIRM_PURCHASE` continua
separada dele porque a ação é outra — registrar como comprado algo que **nunca
esteve** na lista não é o mesmo que baixar um item que está.

## Mensagem nova com pergunta em aberto (decidido em 2026-09-16)

O último gatilho desta seção — "se 'repete a pergunta' se mostrar irritante na
prática" — foi fechado pela ADR-0029, e por um motivo mais forte que irritação:
em pendência de criação de categoria, a mensagem sobre outro assunto ia para uma
chamada em que **só** `confirmarCategoriaSugerida` estava declarada. O modelo não
tinha como dizer "isto não responde à pergunta", e a mensagem podia virar
categoria errada levando junto o valor guardado na pendência.

Agora: uma chamada, com as ferramentas do dia a dia mais a correção quando há
categoria oferecida a corrigir. Superar a pergunta exige **confiança alta** —
superar é destrutivo, e quem responde `mercado` em vez de `1` está respondendo.
A pendência superada tem `expires_at` gravado para agora: para de interceptar o
fio, continua sem `resolution`, continua na central de pendências.

Duas consequências que valem registro:

- mensagem que não é atalho com pendência aberta passa a custar uma chamada ao
  modelo em três tipos de pendência que antes custavam zero;
- a regra 6 do `CLAUDE.md` continua intacta: `sim`, `não`, `desfazer` e número
  seguem resolvidos antes de qualquer chamada. O que sumiu foi a *segunda*
  chamada — agora é uma só por mensagem, com ou sem pendência aberta.

## Gatilhos de revisão

- **Etapa 3**: `fecharCompra` acrescenta um tipo de pendência ("fechar compra
  sem informar valor"). Respondido em 2026-09-16, ver acima: não cabia, o
  desenho de `PendingActionType` mudou, e agora cabe.
- **Etapa 4**: a central de pendências vai ler `question_asked` e `options_json`
  para renderizar UI. A própria ADR-0018 registra que pode não ser suficiente —
  e agora há um dado a mais a considerar: o tipo, que hoje está dentro de
  `intent_json` e não em coluna consultável.
- **Etapa 5**: se a maioria das correções livres for só ajuste de nome, sem
  hierarquia, a ADR-0026 já prevê trocar a segunda chamada ao modelo por uma
  heurística de marcador textual ("dentro de"). O ponto de troca é um só,
  `otherAnswer` no orquestrador.
- ~~Se "repete a pergunta" se mostrar irritante na prática, é aqui que se decide
  deixar a mensagem nova passar por cima da pendência.~~ Decidido em 2026-09-16
  pela ADR-0029, antes de a prática cobrar: o que forçou não foi a irritação e
  sim o lançamento errado silencioso descrito acima.
- **Etapa 5**: medir quantas pendências são superadas por mensagem nova. Se for
  raro, o desempate por confiança alta está apertado demais e a pessoa segue
  presa à pergunta sem que ninguém veja.

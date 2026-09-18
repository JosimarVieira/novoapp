---
tipo: adr
numero: 33
status: aceita
data: 2026-09-18
modulos:
  - conversation
depende_de: [ADR-0004, ADR-0018, ADR-0024, ADR-0029]
supera: []
superada_por:
corrigida_em:
---

# ADR-0033 — Valor ausente com categoria conhecida pergunta o valor, em qualquer faixa de confiança

- **Impacta**: `conversation` (a faixa baixa da
  [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)), e nada mais — nenhum
  módulo de domínio, nenhuma tabela

## Contexto

Em uso real, em 2026-09-18, `petshop` sozinho recebeu **"não entendi essa"**. O
log mostra o que o modelo devolveu:

```json
{"name":"registrarDespesa","arguments":"{\"categoria\": \"Pet shop\", \"confianca\": 0.3}"}
```

A categoria existe na família, `nlu` a resolveu, `categoryId` estava preenchido.
O que faltava era só o valor — e o orquestrador já tem o passo que pergunta
exatamente isso (`registerOrAskAmount`, o mesmo que respondeu "Quanto foi em
Ração?" minutos antes, na mesma conversa). A mensagem não chegou lá: a faixa
baixa da ADR-0004 vem antes e devolve "não entendi".

É a terceira vez que a mesma forma de furo aparece, e por isso ela merece
decisão e não mais um remendo: **o bot sabia o que a pessoa quis e respondeu que
não sabia.** As duas primeiras foram a categoria inexistente engolida pela faixa
baixa (corrigida em 2026-09-17, comentada em `registerExpense`) e o nome novo
escrito no campo errado (ADR-0029, mesma leva).

E há um agravante de registro. O
[SDD de `conversation`](../02-arquitetura/sdd-modulo-conversation.md) já lista,
entre os casos que escapam da faixa de confiança, "**valor ausente** sempre
pergunta, porque não há valor a adivinhar". A frase está escrita desde a Etapa
2a; o código nunca a cumpriu na faixa baixa, e nenhuma ADR a sustentava — então
não havia o que cobrar dela. É o mesmo padrão da ADR-0015, que atravessou duas
etapas sendo contrariada, com os papéis trocados: aqui o design ficou escrito e
a decisão é que faltava. Esta ADR é o lastro que faltava, e ela fixa a ordem —
não só a frase.

Por que o modelo reporta 0,3 aqui é conhecido e está escrito na própria
descrição do parâmetro: ele deve usar "valor intermediário quando falta o
valor". Ele usa 0,3. A ADR-0004 já registra que o `confianca` que o modelo
reporta é mal calibrado — a decisão abaixo é sobre não deixar essa má calibração
descartar informação que o próprio payload carrega.

## Decisão

Quando a intenção é despesa, a categoria está **resolvida** (`categoryId`
preenchido, ou seja uma categoria que já existe nesta família) e o valor está
ausente, o bot pergunta o valor — **em qualquer faixa de confiança**, inclusive
a baixa.

A pergunta é a que já existe (`askAmount`, pendência `ASK_AMOUNT`, TTL da
ADR-0018). Nada é gravado antes da resposta, e responder um número resolve a
pendência pelo curto-circuito determinístico, sem nova chamada de modelo.

Ordem final dentro de `registerExpense`, que é o que esta ADR fixa:

1. categoria inexistente com nome sugerido → oferece criar (ADR-0024)
2. **categoria resolvida sem valor → pergunta o valor (esta ADR)**
3. confiança baixa → "não entendi"
4. confiança média → confirma ou numera opções (ADR-0029)
5. executa

## Consequências

**A favor.** A faixa baixa deixa de descartar mensagem cujo conteúdo o sistema
já entendeu. `petshop`, `mercado`, `farmacia` — categoria sem valor é uma das
formas mais curtas e mais prováveis de se escrever no chat, e ela passa a ter
resposta útil em vez de rejeição.

**Contra, e é real.** Confiança baixa é o modelo avisando que está adivinhando.
Uma mensagem que não é despesa nenhuma, para a qual o modelo chutou uma
categoria existente e nenhum valor, agora recebe "Quanto foi em X?" em vez de
"não entendi". O custo disso é uma pergunta boba, respondida com `não`, sem nada
gravado; o custo do desfecho anterior era reescrever a mensagem inteira sem
saber o que o bot não entendeu. Aceitamos a pergunta boba pelo mesmo motivo da
ADR-0024: perguntar com um nome concreto dentro ensina; "não entendi" não.

**Não vale para o resto.** Item de lista, marcar comprado, convite e consulta
continuam como estão: nenhum deles tem a assimetria "uma entidade resolvida e
uma faltando" que esta decisão explora.

**O que ela não conserta.** Confiança 0,3 numa mensagem clara continua errada, e
continua contaminando a calibração da Etapa 5. Esta ADR compensa o sintoma no
lugar onde o dado existe; a calibração é outro problema, e o dado dela ainda não
está sendo gravado por inteiro ([decisões abertas](../DECISOES-ABERTAS.md)).

## Alternativas

**Baixar o limiar da faixa baixa.** Resolveria `petshop` e afrouxaria todo o
resto junto, inclusive as intenções que gravam sem perguntar. O problema não é o
limiar: é a faixa decidir antes de olhar o que já estava resolvido no payload.

**Perguntar o valor só na faixa média.** Não cobre o caso real, que chegou em
0,3. Seria escrever a decisão de modo a não corrigir o que a motivou.

---
tipo: adr
numero: 18
status: aceita
data: 2026-09-04
modulos:
  - banco
  - conversation
  - web
depende_de:
  - ADR-0004
supera: []
superada_por:
---

# ADR-0018 — Ação pendente expirada no chat escala para a web, nunca é descartada

- **Impacta**: banco (`pending_action`, sem mudança de schema), `conversation`,
  Etapa 4 (PWA); depende de [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)

## Contexto

O SDD já descreve o comportamento de hoje: "`PendingAction` expirada →
Responde que expirou e repete a pergunta original" — ou seja, se o TTL
([decisão aberta #8](../DECISOES-ABERTAS.md)) passa antes do usuário responder no chat, a única saída
documentada era o bot perguntar de novo do zero na próxima mensagem.

O autor pediu, ao descrever a visão da Etapa 4: toda interação do chat que
ficou pendente de decisão do usuário — não só lançamento financeiro —
precisa aparecer dentro do app como notificação, com uma tela onde o
usuário resolve a pendência ali, mesmo depois do TTL do chat ter passado.
"Expirado" não pode significar "perdido".

## Decisão

`pending_action.resolution` continua `NULL` enquanto ninguém resolveu —
`expires_at` no passado deixa de disparar qualquer mudança automática de
estado. O que `expires_at` no passado muda é só o **caminho de resolução**:

- **Dentro do TTL, no chat**: resposta curta (`sim`, número, `desfazer`)
  resolve por curto-circuito determinístico (regra 6 do CLAUDE.md), sem LLM
  — comportamento já decidido, sem mudança.
- **Fora do TTL, no chat**: resposta curta não é mais tratada como resposta
  à pendência (o atalho não existe mais) — o bot informa que expirou no chat
  e aponta para o app, além de repetir a pergunta ali mesmo (mantém o
  comportamento já documentado, não o substitui).
- **Fora do TTL, na web (Etapa 4)**: toda `pending_action` com
  `resolution IS NULL` aparece na "central de pendências" do app, com
  notificação. O usuário resolve ali com a mesma pergunta e as mesmas opções
  que foram geradas no chat (`question_asked`, `options_json`) —
  `resolution` vira `CONFIRMED` ou `REJECTED` pela ação na web, exatamente
  como já seria pelo chat.

Nenhuma coluna nova: a consulta "o que está pendente" já é
`WHERE household_id = ? AND resolution IS NULL`, ordenável por `expires_at`
para separar "ainda no prazo do chat" de "só resolve pelo app agora".

Isso vale para toda `pending_action`, não só financeira — recorrência de
tarefa, item de lista ambíguo, qualquer fluxo que gere uma pergunta de
confirmação usa o mesmo mecanismo, sem desenho separado por domínio.

## Alternativas consideradas

### A. Manter o comportamento atual: expirado é só reperguntado no chat, sem tela na web
Descartada: o autor identificou que isso é uma perda de trabalho real — o
usuário já tinha respondido a intenção original (`mercado 50`), só não
respondeu a pergunta de desambiguação a tempo; forçar reiniciar do zero no
chat é pior experiência do que só apontar pra uma tela onde a pergunta
ainda está esperando.

### B. Marcar `resolution = EXPIRED` automaticamente quando `expires_at` passa (job ou trigger)
Descartada: exigiria infraestrutura de job que o projeto já evitou em caso
parecido ([ADR-0014](0014-fechamento-de-fatura-sob-demanda.md), fatura calculada sob demanda, não por job) — e "expirado"
viraria estado terminal antes de a pessoa ter chance de resolver na web,
contradizendo o próprio objetivo desta ADR. `resolution IS NULL` já
distingue "ainda em aberto" de "resolvido" sem precisar de um terceiro
estado calculado por tempo.

## Consequências

### Positivas
- Nenhuma pergunta feita pelo bot se perde por o usuário não ter respondido
  a tempo no chat — só muda onde ela é resolvida.
- Zero mudança de schema: a mesma tabela já projetada em ADR-0004/modelo de
  dados serve para isso, só muda a query e a superfície (web) que a lê.
- Mecanismo único para qualquer tipo de pendência (financeira, lista,
  tarefa) — não precisa desenhar central de notificação por domínio.

### Negativas
- Sem UI ainda: esta ADR decide o mecanismo (schema e regra de resolução),
  não a tela — o design da "central de pendências" é trabalho real da
  Etapa 4, não incluído aqui.
- `pending_action` acumula indefinidamente enquanto ninguém resolve — sem
  job de limpeza, uma pendência ignorada para sempre fica na tabela para
  sempre. Retenção entra no mesmo escopo LGPD já registrado ([decisão aberta #4](../DECISOES-ABERTAS.md)),
  não resolvido aqui.
- Notificação em si (como o app avisa o usuário que há pendência — push,
  badge, e-mail) não é decidida nesta ADR.

## Gatilhos de revisão

Quando a Etapa 4 desenhar a tela de fato, revisitar se `question_asked` e
`options_json` (pensados originalmente para texto de chat) são suficientes
para render de UI, ou se precisam de um formato mais estruturado.

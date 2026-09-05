---
tipo: adr
numero: 12
status: aceita
data: 2026-08-31
modulos:
  - banco
  - finance
depende_de: []
supera: []
superada_por:
---

# ADR-0012 — Qualquer membro edita lançamento de qualquer outro membro do household

- **Impacta**: banco (`transaction`), módulo de finanças, UX de recibo/correção
  (Etapa 4); resolve [decisão aberta #9](../DECISOES-ABERTAS.md)
- **Termos**: [Lançamento, Membro](../00-produto/glossario.md#núcleo)

## Contexto

`docs/DECISOES-ABERTAS.md` registrava como aberto: "Membro pode editar
lançamento de outro membro? Envolve confiança dentro da família. Provavelmente
sim com rastro de quem editou, mas precisa de opinião."

O modelo de dados atual não tem conceito de dono exclusivo de dado dentro do
household. `household_id` é a única fronteira de isolamento (ADR-0003);
`member_id` em `transaction` (via `created_by_member_id`, ver
`docs/02-arquitetura/modelo-de-dados.md`) registra quem lançou, não quem tem
permissão de mexer. `role` (`OWNER`/`MEMBER`) hoje só existe para governar o
vínculo com o household em si — quem administra a assinatura — nunca para
controlar acesso a dado de domínio dentro da família (ADR-0007).

Finanças compartilhada é o caso de uso central do produto: o casal lança,
consulta e corrige o mesmo extrato. Tratar o lançamento como "propriedade" de
quem o criou introduz uma noção de posse individual que não existe em nenhuma
outra regra do sistema.

## Decisão

Qualquer membro do household pode editar ou estornar (`desfazer`, regra 7 do
CLAUDE.md) um lançamento registrado por qualquer outro membro do mesmo
household — não só o próprio. Toda edição é auditável: fica registrado quem
editou e quando, no mesmo espírito do padrão já usado em `transaction`
(`reversed_at`/`reversal_of_id` em vez de delete).

**Schema decidido em 2026-09-04, a pedido do autor** ("precisa de histórico
de transação com o usuário vinculado, para saber quem fez a alteração e o
que fez"): dois mecanismos, um para cada tipo de mudança, não um único
campo genérico.

- **Estorno** (fato único, acontece no máximo uma vez por lançamento):
  `transaction.reversed_by_member_id`, ao lado de `reversed_at`/`reversal_of_id`
  que já existiam — mesmo padrão de `purchased_by_member_id` em `list_item`.
- **Correção de campo** (pode acontecer mais de uma vez): tabela nova
  `transaction_edit`, append-only, uma linha por edição —
  `id, household_id, transaction_id, edited_by_member_id, edited_at,
  changed_fields_json` (JSON com `{"campo": [valor_de, valor_para]}` por
  campo alterado, mesmo padrão de `intent_json`/`options_json` já usado em
  `inbound_message`/`pending_action`). Não cobre criação — isso já é
  `transaction.created_by_member_id` — nem estorno, coberto pelo mecanismo
  acima.

Ver [modelo-de-dados.md](../02-arquitetura/modelo-de-dados.md) para o schema completo. Nenhuma auditoria
existente no legado resolvia isso: `Transaction` lá não tinha campo de
atualização nem histórico — o único artefato próximo era um `Log` genérico
de texto livre, sem estrutura por campo. Não há conhecimento validado do
legado a portar aqui; é desenho novo.

## Alternativas consideradas

### A. Só quem lançou edita o próprio lançamento
Descartada: cria atrito estrutural numa família pequena — a pessoa que
percebe o erro no extrato (normalmente não quem lançou) precisaria pedir para
a outra corrigir, toda vez. Contraria o caso de uso central do produto
(finanças compartilhada, não paralela).

### B. Só `OWNER` edita lançamento de outro membro
Descartada: introduziria a primeira regra de autorização por papel sobre
dado de domínio em toda a base. Hoje `role` não governa nada além do vínculo
com o household (ADR-0007); usar `OWNER`/`MEMBER` para isso também
transformaria uma distinção pensada para billing/administração em hierarquia
de confiança dentro da família — problema diferente, não necessariamente
correlacionado (a pessoa que administra a assinatura não é necessariamente a
mais "confiável" com o extrato).

## Consequências

### Positivas
- Consistente com a única fronteira de isolamento que o sistema já reconhece
  (household, não membro) — não introduz um segundo modelo de permissão.
- Sem atrito: qualquer um corrige o que vir de errado, sem depender de quem
  lançou estar disponível.
- Reaproveita o padrão de auditoria já decidido para `transaction`
  (estorno em vez de delete) em vez de desenhar um mecanismo novo.

### Negativas
- Risco novo, aceito e não eliminado: um membro pode editar ou estornar
  lançamento de outro sem consentimento prévio — por engano ou por conflito
  familiar. Mitigado por rastro de autoria (`transaction_edit`,
  `reversed_by_member_id`) e por `desfazer`, não impedido — a família ainda
  descobre o que aconteceu só depois de já ter acontecido.
- `changed_fields_json` sem schema fixo por campo é flexível mas não
  validável em banco — um bug na camada de serviço pode gravar um diff
  incoerente (ex. campo que não existe em `transaction`) sem o banco
  recusar. Mitigação é teste de unidade em `finance`, não constraint de SQL.
- Nenhuma granularidade fica disponível se um household futuro quiser
  privacidade parcial (ex.: presente, despesa pessoal) — essa ADR fecha essa
  porta para o modelo atual.

## Gatilhos de revisão

Se, na Etapa 5, edição sem consentimento aparecer como reclamação real (não
hipotética) entre os membros de uma família testando o produto, avaliar
permissão mais granular — por exemplo, notificar o autor original quando
outro membro edita, antes de ir direto para controle por papel.

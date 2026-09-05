---
description: Auditoria de consistência entre todos os documentos
---

Audite a coerência da documentação. Não escreva código. Não corrija nada
antes de me mostrar a lista.

Verifique e reporte:
1. **Contradições** entre CLAUDE.md, ADRs aceitas e SDDs.
2. **ADRs órfãs** — decisão aceita que nenhum SDD reflete.
3. **SDD sem lastro** — design que não deriva de nenhuma ADR nem feature.
4. **Features sem cenário negativo** (ambiguidade ou erro).
5. **Termos fora do glossário**, ou dois nomes para a mesma coisa.
6. **Regras não negociáveis do CLAUDE.md** que nenhum documento operacionaliza.
7. **Escopo vazando** — detalhamento de coisa fora das Etapas 1 a 3.

Checagens mecânicas (rode antes das semânticas — são baratas e pegam o tipo
de erro que passa despercebido em leitura):

8. **Integridade do frontmatter.** Toda ADR em `docs/01-adr/` (exceto
   `TEMPLATE.md`) tem frontmatter completo; `numero` bate com o prefixo do
   arquivo; a sequência não tem buraco nem repetição; `status` é um de
   `proposta`/`aceita`/`superada`; `superada_por` e `depende_de` apontam para
   ADR que existe; `supera`/`superada_por` são recíprocos; `modulos` só usa o
   vocabulário fechado do TEMPLATE; ADR `superada` não é citada como
   `depende_de` por nenhuma ADR vigente. O mesmo para `tipo: sdd` em
   `docs/02-arquitetura/`.
9. **Índice manual desatualizado.** Qualquer contagem ("as 17 ADRs"), faixa
   ("ADRs 0001-0017") ou lista de arquivos digitada à mão em `docs/README.md`,
   `ROADMAP.md` ou `CLAUDE.md` que não bata com o disco. Duas precisões, ou a
   checagem vira alarme falso: compare com o total **daquele status** (texto
   que diz "aceitas" compara com `status: aceita`, não com o total de
   arquivos), e avalie só a maior faixa de cada arquivo — referência
   explicitamente histórica ("as 0001-0006 originalmente previstas") não é
   desatualização. Regra de fundo: índice que dá para derivar do frontmatter
   não se mantém à mão — a correção proposta é substituir pela base
   (`docs/adr.base`), nunca atualizar o número, que rot novamente.
10. **Frontmatter contra prosa.** `modulos` do frontmatter aparece no corpo
    da ADR, e `depende_de` bate com as ADRs efetivamente citadas no texto.
    Compare ignorando acento e usando o par tag↔prosa do TEMPLATE: a tag é em
    inglês e o texto é em português por convenção do projeto, então `nlu` no
    frontmatter e "interpretação por chat" no corpo **são a mesma coisa** e
    não devem ser reportados. Só é divergência quando o módulo não aparece em
    nenhuma das duas formas — aí diga qual dos dois lados está errado.
    Severidade baixa: esta checagem é heurística, nunca bloqueie nada com
    ela.

Saída: lista priorizada por severidade, cada item com arquivo, trecho e a
correção proposta em uma linha. Sem preâmbulo. Se as checagens 8-10 passarem
todas, diga isso em uma linha em vez de detalhar.

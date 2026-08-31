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

Saída: lista priorizada por severidade, cada item com arquivo, trecho e a
correção proposta em uma linha. Sem preâmbulo.

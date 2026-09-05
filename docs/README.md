# Índice da documentação

Ordem de leitura para quem chega agora:

1. `../CLAUDE.md` — o que é o produto e as regras não negociáveis
2. `00-produto/glossario.md` — a linguagem. Sem isso, nada mais faz sentido
3. `01-adr/` — por que as coisas são como são.
   No Obsidian, [`adr.base`](adr.base) é o índice: filtra por status, módulo e
   dependência sem abrir arquivo
4. `02-arquitetura/modelo-de-dados.md` — as entidades
5. `02-arquitetura/sdd-visao-geral.md` — como os módulos se encaixam
6. `03-specs/features/` — o que o sistema faz, do ponto de vista do usuário
7. [`DECISOES-ABERTAS.md`](DECISOES-ABERTAS.md) — o que ainda não sabemos
8. `../ROADMAP.md` — em que ordem

## Estado atual

| Documento | Estado |
|---|---|
| CLAUDE.md | Escrito |
| Glossário | Escrito, aberto a novos termos |
| ADRs | Índice vivo em [`adr.base`](adr.base) (Obsidian). Fora do Obsidian, navegue em [`01-adr/`](01-adr/) |
| Modelo de dados | Escrito, cobre Etapas 1-3, com diagrama Mermaid |
| SDD visão geral | Escrito |
| SDD por módulo | Parcial — os 5 que a Etapa 1 toca estão escritos: `channel`, `identity`, `nlu`, `conversation`, `finance` (cada um com uma seção "Escopo desta versão" marcando o que fica pra Etapa 2). `shopping`/`tasks` ficam pra quando a etapa deles chegar |
| Feature: finanças | Escrita |
| Feature: mercado | Escrita |
| Feature: elo | Escrita |
| Feature: tarefas | **Não escrita** |
| Feature: vínculo de identidade | Escrita — ADR-0020 (convite de membro) Aceita |
| Estratégia de testes | Escrita |
| Visão de produto e personas | **Não escrita** |

Documentação de Etapas 4+ é deliberadamente ausente. Escrever agora produziria
ficção: as premissas sobre ambiguidade e confirmação só se provam com uso real.

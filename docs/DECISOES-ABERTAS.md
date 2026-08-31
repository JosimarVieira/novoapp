# Decisões em aberto

O que ainda não foi decidido, e por que ainda não. Item aqui não vira premissa
silenciosa no código.

Ordenado por custo de errar.

## Bloqueiam a Etapa 6-7 (comercial)

| # | Questão | Por que ainda não decidida | Quando decidir |
|---|---|---|---|
| 2 | Preço e mecânica atual de template da Meta | Muda com frequência; qualquer número de hoje envelhece | Antes de definir mensalidade |
| 3 | Modelo de LLM e provedor | Depende do custo por mensagem medido em uso real | Etapa 5 |
| 4 | Retenção de `inbound_message` | Escopo LGPD; depende do que a Etapa 5 mostrar ser necessário para calibração | Antes do primeiro cliente externo |
| 5 | Base legal LGPD e fluxo de exclusão de conta | — | Etapa 6, antes de qualquer cliente |

## Bloqueiam decisões de produto

| # | Questão | Nota |
|---|---|---|
| 7 | Limiar de confiança entre executar e perguntar | Inventar número agora é chute. Sai da Etapa 5 com dado. |
| 8 | TTL de `PendingAction` | Sugerido 10 min, sem base. Calibrar na Etapa 5. |
| 9 | Membro pode editar lançamento de outro membro? | Envolve confiança dentro da família. Provavelmente sim com rastro de quem editou, mas precisa de opinião. |
| 10 | Contas (`account`) entram na Etapa 1 ou depois? | Complica o parsing ("mercado 50" não diz de qual conta saiu). Talvez conta padrão implícita. |
| 11 | Categorias iniciais no onboarding, ou household começa vazio? | Vazio força o fluxo de criação por chat (bom para testar), mas piora a primeira impressão. |

## Não bloqueiam nada ainda

| # | Questão |
|---|---|
| 12 | Recorrência de tarefas — modelo de dados |
| 13 | Anexo de foto de cupom fiscal no chat |
| 14 | Orçamento por categoria e alertas |
| 15 | Múltiplas listas de compras simultâneas (mercado, farmácia, feira) |

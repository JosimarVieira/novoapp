# ADR-0004 — Interpretação por function calling com política de confiança

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: `nlu`, `conversation`, UX do chat

## Contexto

A proposta de valor é ser mais rápido que abrir o app. Se cada mensagem gera
uma pergunta de confirmação, o produto perde a razão de existir. Se nunca
confirma, um erro de interpretação vira dado financeiro errado silencioso —
o que é pior.

Mensagens reais são curtas, ambíguas e sem pontuação: `mercado 50`,
`50 mercado`, `paguei o mercado`, `acabou arroz e leite`.

## Decisão

O LLM recebe as **ferramentas declaradas** (`registrarDespesa`,
`registrarReceita`, `adicionarItemLista`, `marcarItemComprado`, `criarTarefa`,
`consultarLista`, `consultarSaldo`, `fecharCompra`) e as categorias e listas
reais do household como contexto. Nunca pedimos JSON livre.

A saída é uma `Intent` tipada com `confidence`. A política é:

| Situação | Comportamento |
|---|---|
| Confiança alta, entidades resolvidas | Executa e responde recibo com saída `desfazer` |
| Confiança média, ou categoria inexistente | UMA pergunta com opções numeradas |
| Confiança baixa, ou nenhuma tool escolhida | Pergunta aberta curta, sem adivinhar |

Confirmações (`sim`, `não`, número, `desfazer`) são resolvidas por
curto-circuito determinístico, sem chamar o modelo.

O limiar exato entre alta e média é **[a definir na Etapa 5]**, com dados reais.
Qualquer número escolhido agora seria inventado.

## Alternativas consideradas

### A. Parser por regex e gramática fixa
Descartada: cobre `mercado 50` e quebra em `paguei o mercado hoje, uns 50`.
Manter a gramática cresce sem limite e o usuário aprende a falar como robô.

### B. LLM devolvendo JSON livre por prompt
Descartada: sem contrato validável, erro de formato vira exceção em produção.
Function calling dá schema, validação e recusa explícita quando nada encaixa.

### C. Sempre confirmar antes de gravar
Descartada: mata a proposta de valor. Reintroduzida parcialmente pela política
de confiança, que é o meio termo.

## Consequências

### Positivas
- Erro é recuperável em um toque (`desfazer`), o que permite ser agressivo na
  execução automática.
- Categorias reais no contexto reduzem alucinação de categoria inexistente.

### Negativas
- `confidence` reportado por LLM é mal calibrado por natureza. O limiar terá
  que ser calibrado empiricamente, e pode não ser estável entre versões do
  modelo. **Esta é a fraqueza central da decisão.**
- Contexto cresce com o número de categorias do household; famílias com
  dezenas de categorias podem degradar a precisão.
- Dependência de disponibilidade e latência de um terceiro no caminho crítico.

## Gatilhos de revisão

Se na Etapa 5 a taxa de acerto ficar abaixo de 90% no fluxo de despesa, revisar
antes de qualquer feature nova. Precisão do interpretador é o produto.

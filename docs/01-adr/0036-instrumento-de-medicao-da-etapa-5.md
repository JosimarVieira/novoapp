---
tipo: adr
numero: 36
status: aceita
data: 2026-09-19
modulos:
  - nlu
  - channel
  - banco
depende_de: [ADR-0003, ADR-0005, ADR-0009, ADR-0035]
supera: []
superada_por:
corrigida_em:
---

# ADR-0036 — Proveniência por impressão digital, e versionar o prompt em vez de congelá-lo

- **Impacta**: banco (`inbound_message` ganha duas colunas), `nlu` (a fronteira
  `MessageInterpreter` passa a responder quem interpretou), `channel` (grava),
  `conversation` (carrega no `ProcessingOutcome`), a Etapa 5 do
  [ROADMAP](../../ROADMAP.md) e a posição interina da
  [decisão aberta #4](../DECISOES-ABERTAS.md); torna executável a
  [ADR-0035](0035-o-que-a-etapa-5-mede.md), que decidiu **o que** medir e deixou
  o instrumento em aberto

## Contexto

A [ADR-0035](0035-o-que-a-etapa-5-mede.md) fechou as três métricas e terminou
dizendo que nenhuma delas é calculável hoje: `inbound_message` grava
`intent_json` e `confidence`, e nada sobre **quem** produziu aquilo.

O que torna isso urgente não é a teoria. Entre 2026-09-16 e 2026-09-19 o prompt
mudou quatro vezes, o parâmetro de valor foi renomeado e teve a semântica trocada
(`valor_cents` em centavos → `valor` em reais), e a
[ADR-0034](0034-valor-so-vale-se-a-pessoa-escreveu-digito.md) registra a mesma
palavra — `mercado` — produzindo **quatro desfechos diferentes em cem segundos**,
com `temperature` 0. Uma taxa calculada sobre esse período mede a média de quatro
sistemas diferentes e não diz nada sobre nenhum deles.

Há ainda um conflito prático que o desenho precisa resolver, e que não é
secundário: a Etapa 5 dura quatro semanas, e três dos quatro incidentes recentes
eram **dinheiro errado**. Qualquer regra que exija não tocar no prompt durante a
medição é uma regra que manda deixar dinheiro errado de pé — e ela não seria
cumprida, com razão.

Sobre a duas mudanças de comportamento mais recentes, vale notar de onde vieram:
não da lógica, mas da **descrição de um parâmetro** — `confianca`
([decisão aberta #22](../DECISOES-ABERTAS.md)) e `marcarItemComprado`
([#25](../DECISOES-ABERTAS.md)). É o tipo de edição que ninguém trata como
"versão nova", e é exatamente a que muda o resultado.

## Decisão

### 1. Duas colunas em `inbound_message`, nulas quando não houve modelo

`prompt_version` e `model_name` (`V6__interpretation_provenance.sql`). Nulo
significa **"nenhum modelo opinou"** — curto-circuito da regra 6, onboarding,
falha antes da interpretação —, e não "esqueceram de gravar". A distinção é o que
permite separar denominadores sem inventar um terceiro estado.

A fronteira `MessageInterpreter` ganha `provenance()`, **sem `default`**:
adaptador novo de provedor tem de responder essa pergunta antes de entrar em
produção. Quem grava continua sendo `channel`, pelo caminho que já existe — o
valor sobe dentro do `ProcessingOutcome`, porque `nlu` não persiste nada e
`conversation` não pode importar `channel`.

### 2. `prompt_version` é hash, e cobre só a parte estática

Impressão digital SHA-256 (12 caracteres) sobre os textos de prompt, as tools
declaradas, e o **nome e a descrição de cada parâmetro**. Fica de fora o contexto
injetado por mensagem — categorias e itens do household, pergunta pendente.

Hash, e não uma constante incrementada à mão, pelo mesmo motivo que a
[ADR-0003](0003-isolamento-multi-tenant-por-household.md) recusou filtro manual
de tenant: constante depende de alguém lembrar, e o que o código consegue
garantir não se deixa para a disciplina.

A descrição de cada parâmetro entra **porque é ela que muda o comportamento** —
ver Contexto. A leitura dela é por reflexão, porque os tipos de schema do
LangChain4j não compartilham um `description()` em interface comum; falhar
degrada para o nome do tipo em vez de quebrar a interpretação, e
`InterpretationFingerprintTest` existe para que essa degradação não passe
silenciosa num upgrade da biblioteca.

### 3. Não se congela o prompt durante a medição. Versiona-se

A métrica é calculada **por `prompt_version`**. Uma correção abre uma janela
nova; não contamina a anterior, e não precisa de permissão de ninguém. O portão
da Etapa 6 olha a versão vigente com volume suficiente.

Isso troca uma regra que ninguém cumpriria por uma que o dado cumpre sozinho — e
mantém verdadeira a única coisa que não pode deixar de ser verdade durante quatro
semanas de uso real: **dinheiro errado se conserta no dia em que aparece.**

### 4. O gabarito mora fora do banco, versionado no repositório

`docs/04-qualidade/nlu-eval/`. O gabarito é artefato de engenharia: precisa de
diff, de revisão, e de ser congelado entre duas medições — que é o gatilho de
revisão da [ADR-0035](0035-o-que-a-etapa-5-mede.md) e é literalmente o que git
faz. Guardá-lo em coluna de `inbound_message` significaria "congelar" comparando
dois estados de uma tabela viva, sem diff e sem revisão.

Nome próprio é substituído por marcador ao anotar. É mitigação, não solução: ver
Negativas.

### 5. Retenção, até a Etapa 6: retém tudo, deliberadamente

Enquanto o produto está em construção e validação na família do fundador, toda
`inbound_message` é mantida — é o único corpus que existe para calibrar. A
[decisão aberta #4](../DECISOES-ABERTAS.md) **continua aberta** e é decidida na
Etapa 6, ao desenhar LGPD, antes de qualquer cliente externo. O que esta ADR
acrescenta é que a retenção de hoje passa a ser posição declarada, com prazo, e
não ausência de decisão.

## Alternativas consideradas

### A. `prompt_version` como constante incrementada à mão
Descartada: as duas mudanças de comportamento mais recentes foram edições na
descrição de um parâmetro, e é precisamente o tipo de mudança que quem edita não
registra como versão nova. Uma constante que só muda quando alguém lembra de
mudá-la mente com mais confiança do que não ter coluna nenhuma.

### B. Congelar prompt e modelo durante a janela de medição
Era a proposta original desta conversa, e foi descartada ao ser escrita: as quatro
semanas da Etapa 5 são também quando dinheiro errado aparece, e uma regra que
proíbe consertá-lo não sobrevive ao primeiro incidente. Versionar entrega a mesma
comparabilidade sem pedir que ninguém escolha entre medir e consertar.

### C. Hash incluindo o contexto injetado (categorias e itens do household)
Descartada: cada família teria a própria "versão de prompt", e a versão mudaria a
cada categoria criada. A métrica não agruparia nada — que é a única coisa que a
coluna existe para permitir.

### D. Gabarito em coluna de `inbound_message`
Descartada: anotar é trabalho de revisão, e revisão sem diff não é revisão. Além
disso amplia o escopo LGPD da tabela justamente quando a
[decisão aberta #4](../DECISOES-ABERTAS.md) ainda não foi tomada.

### E. Tabela nova (`interpretation_log`) em vez de duas colunas
Descartada por não resolver nada que as colunas não resolvam: a linha de
`inbound_message` já existe, já é uma por mensagem, e já carrega `intent_json` e
`confidence` pelo mesmo motivo. Uma tabela a mais custaria RLS, grant e join para
guardar dois campos de cardinalidade 1:1.

## Consequências

### Positivas
- As três métricas da ADR-0035 passam de "definidas" a "calculáveis", e o log
  volta a ser comparável consigo mesmo.
- Conserto de dinheiro errado deixa de competir com a medição: são duas janelas,
  não um dilema.
- A fronteira `MessageInterpreter` passa a obrigar todo adaptador futuro a
  declarar quem respondeu — o que faz a troca de provedor da
  [decisão aberta #3](../DECISOES-ABERTAS.md) nascer mensurável.
- Nulo nas colunas vira sinal útil: é a fração de mensagens que o curto-circuito
  resolveu sem gastar modelo, que ninguém media.

### Negativas
- **O hash não diz o que mudou, só que mudou.** Para saber por que uma janela
  ficou pior é preciso ir ao git. Aceitável porque o prompt vive no repositório,
  mas é uma indireção a mais no momento em que se está diagnosticando.
- **Reflexão sobre a API do LangChain4j é acoplamento frágil.** Um upgrade pode
  fazer o hash degradar para o nome do tipo e parar de enxergar mudança de
  descrição. Há teste para isso, e o teste é a única coisa entre a degradação e
  o silêncio.
- **Mensagens reais da família num repositório git.** O gabarito é conteúdo
  pessoal, e substituir nome próprio por marcador reduz o problema sem eliminá-lo.
  Enquanto o repositório for privado e de uma pessoa, é aceitável; deixa de ser
  no instante em que houver colaborador, e isso é anterior à Etapa 6.
- **Versionar em vez de congelar fragmenta o dado.** Se o prompt mudar toda
  semana, nenhuma janela junta volume suficiente para o portão de 90% significar
  algo — e a decisão de quando parar de mexer continua sendo humana. A coluna
  torna a fragmentação visível; não a impede.
- **Retém tudo é a escolha mais cara de reverter.** Quatro semanas de mensagem de
  família guardadas antes de existir política de retenção significam que a
  decisão da Etapa 6 nasce com passivo, não em folha em branco.

## Gatilhos de revisão

- **Ao fechar a Etapa 5**: se nenhuma `prompt_version` tiver juntado volume
  suficiente, o problema não é a coluna — é que o prompt não parou de mudar, e a
  conversa passa a ser sobre quando congelar de fato, aí com dado sobre quanto se
  mexeu.
- **Etapa 6, ao desenhar LGPD** ([decisão aberta #4](../DECISOES-ABERTAS.md)):
  decidir retenção de `inbound_message` e o destino do gabarito anotado. Os dois
  andam juntos e nenhum sobrevive a um cliente externo como está.
- **Se o repositório ganhar um segundo colaborador**: o gabarito com mensagens da
  família sai do git antes disso, não depois.
- **Upgrade do LangChain4j**: se `InterpretationFingerprintTest` quebrar, o
  conserto é a leitura da descrição, nunca remover o teste.

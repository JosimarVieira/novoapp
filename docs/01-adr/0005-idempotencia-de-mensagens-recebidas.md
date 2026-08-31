# ADR-0005 — Idempotência de mensagens recebidas

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: `channel`, `finance`

## Contexto

Telegram e Meta reenviam webhook quando não recebem 200 dentro do timeout.
Processamento assíncrono (necessário para responder rápido) aumenta a janela
de reentrega. No domínio financeiro, processar a mesma mensagem duas vezes
cria uma despesa duplicada — o usuário perde a confiança no saldo, e um app
de finanças em que não se confia no saldo não tem uso.

## Decisão

Toda mensagem recebida é persistida antes de qualquer processamento, com
chave única `(channel, provider_message_id)`. Violação de unicidade é tratada
como reentrega: responde 200 e descarta silenciosamente.

O webhook responde 200 imediatamente após a persistência; a interpretação e a
execução acontecem fora do ciclo de request.

## Alternativas consideradas

### A. Deduplicação por hash de conteúdo e janela de tempo
Descartada: `mercado 50` enviado duas vezes de propósito é caso de uso legítimo
(duas compras iguais no mesmo dia). Hash não distingue reentrega de repetição
intencional; `provider_message_id` distingue.

### B. Processar síncrono e confiar em não dar timeout
Descartada: chamada de LLM tem cauda de latência imprevisível. Um pico
transforma reentrega em duplicidade justamente sob carga.

## Consequências

### Positivas
- Duplicidade financeira eliminada na origem, não mitigada depois.
- A tabela de mensagens recebidas vira o log natural para calibrar o
  interpretador na Etapa 5.

### Negativas
- Toda mensagem recebida fica armazenada, inclusive conteúdo pessoal — vira
  escopo de LGPD e precisa de política de retenção e de exclusão real.
- Assincronia significa que o usuário pode receber o recibo segundos depois;
  se falhar após o 200, o usuário não sabe. Exige caminho de erro que avise
  no chat, e não apenas log.

## Gatilhos de revisão

Definir retenção das mensagens recebidas antes do primeiro cliente externo.

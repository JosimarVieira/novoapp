# ADR-0003 — Isolamento multi-tenant por household

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: banco, todos os módulos de domínio

## Contexto

Produto comercial armazenando dados financeiros de famílias. Vazamento entre
households não é bug de severidade alta — é evento de encerramento do negócio,
com exposição LGPD. O ponto de entrada mais comum do sistema é uma mensagem de
chat, onde o tenant é inferido de um identificador externo, não de uma sessão
autenticada. Isso amplia a superfície de erro.

## Decisão

`household_id` obrigatório em toda tabela de dado de usuário. Isolamento
aplicado por mecanismo automático, nunca por filtro escrito à mão na query:
Row Level Security no Postgres com `SET LOCAL app.household_id` por transação,
definido em um único ponto de entrada.

Nenhuma query de domínio pode ser escrita assumindo que o desenvolvedor
lembrará de filtrar.

## Alternativas consideradas

### A. Filtro manual em cada repositório
Descartada: depende de disciplina em 100% dos casos, para sempre, inclusive
sob pressa. A taxa de erro tende a 1 com o tempo, e a consequência é fatal.

### B. Banco (ou schema) por household
Descartada nesta fase: migrations multiplicadas por N tenants e provisionamento
por signup, custo operacional alto demais para validação. Reavaliar apenas se
um cliente exigir isolamento físico.

### C. Filtro Hibernate (`@FilterDef`) por interceptor
Não descartada, mas insuficiente sozinha: protege o caminho JPA e deixa
passar query nativa e acesso administrativo. Pode ser usada **em conjunto** com
RLS, nunca no lugar.

## Consequências

### Positivas
- Isolamento garantido inclusive em query nativa e em script de manutenção.
- A garantia vive no banco, não na aplicação — sobrevive a refactor.

### Negativas
- RLS mal configurado falha silenciosamente (retorna vazio em vez de erro),
  o que é confuso de diagnosticar.
- Exige conexão que não seja superusuário e cuidado com pool de conexões:
  `SET LOCAL` precisa estar amarrado à transação, ou vaza contexto entre
  requisições que reusam a mesma conexão. **Este é o risco real da decisão.**
- Jobs em background precisam definir contexto explicitamente.

## Gatilhos de revisão

Se o custo de diagnosticar falhas de RLS se mostrar alto na Etapa 5, avaliar
adicionar teste de integração que roda o suite inteiro com dois households
simultâneos e falha se houver qualquer vazamento.

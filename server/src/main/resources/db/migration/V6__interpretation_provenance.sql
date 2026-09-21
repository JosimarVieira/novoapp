-- ADR-0035, o instrumento: sem saber QUAL prompt e QUAL modelo produziram cada
-- interpretacao, as tres metricas da Etapa 5 nao sao calculaveis -- o log de uso
-- real nao e comparavel consigo mesmo.
--
-- O problema nao e hipotetico. Entre 2026-09-16 e 2026-09-19 o prompt mudou
-- quatro vezes e o modelo devolveu quatro desfechos diferentes para a palavra
-- "mercado" em cem segundos, com temperature 0 (ADR-0034). Misturar essas
-- mensagens numa taxa so mede a media de quatro sistemas diferentes.
--
-- A decisao que acompanha estas colunas: nao se congela o prompt durante a
-- medicao -- congelar exigiria deixar dinheiro errado de pe por quatro semanas,
-- e tres dos quatro incidentes recentes eram exatamente isso. Versiona-se. A
-- metrica e calculada POR prompt_version, e uma correcao abre uma janela nova em
-- vez de contaminar a anterior.
--
-- prompt_version e hash, nao constante bumped a mao: constante depende de
-- alguem lembrar, e a ADR-0003 ja estabeleceu que este projeto nao confia em
-- disciplina para o que o codigo consegue garantir. Cobre a parte ESTATICA do
-- que vai ao modelo (textos de prompt, tools declaradas, descricao de cada
-- parametro) e nao o contexto injetado por mensagem (categorias e itens do
-- household), que varia por familia e nao e versao de nada.
--
-- Nulo em mensagem que nao gastou chamada de modelo: curto-circuito da regra 6,
-- onboarding, reentrega descartada. Nulo aqui significa "nenhum modelo opinou",
-- e nao "esqueceram de gravar".
ALTER TABLE inbound_message
    ADD COLUMN prompt_version text,
    ADD COLUMN model_name     text;

COMMENT ON COLUMN inbound_message.prompt_version IS
    'Hash da parte estatica do prompt e das tools (ADR-0035). Nulo quando nao houve chamada de modelo.';
COMMENT ON COLUMN inbound_message.model_name IS
    'Modelo que produziu a interpretacao (ADR-0035). Nulo quando nao houve chamada de modelo.';

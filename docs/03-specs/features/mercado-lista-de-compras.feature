# language: pt

# Tag no nivel da Funcionalidade: os nove cenarios daqui sao todos da mesma
# etapa, entao a tag e herdada em vez de repetida linha a linha. Diferente de
# financas e vinculo, que sao mistos e precisam de tag por cenario.
@etapa2
Funcionalidade: Lista de compras compartilhada por chat
  Como membro de um household
  Quero avisar o que está faltando e consultar a lista pelo chat
  Para que qualquer pessoa da família compre sem precisar combinar antes

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" e "Bruno" são membros do household "Silva" com Telegram vinculado
    E que existe uma lista de compras ativa no household "Silva"

  Cenário: Adicionar item pela linguagem natural
    Quando "Ana" envia "acabou o arroz"
    Então o item "Arroz" entra na lista de compras com status pendente
    E o item fica registrado como solicitado por "Ana"
    E "Ana" recebe um recibo confirmando o item

  Cenário: Adicionar vários itens em uma mensagem
    Quando "Ana" envia "acabou arroz, leite e café"
    Então os itens "Arroz", "Leite" e "Café" entram na lista como pendentes
    E "Ana" recebe um único recibo listando os três itens

  Cenário: Item com quantidade
    Quando "Ana" envia "precisa de 2 kg de arroz"
    Então o item "Arroz" entra na lista com quantidade 2 e unidade "kg"

  Cenário: Item já pendente na lista
    Dado que o item "Arroz" já está pendente na lista
    Quando "Bruno" envia "acabou o arroz"
    Então a lista continua com um único item "Arroz" pendente
    E "Bruno" é informado de que o item já estava na lista, pedido por "Ana"

  Cenário: Consultar o que está faltando
    Dado que os itens "Arroz" e "Leite" estão pendentes
    E que o item "Café" já foi marcado como comprado
    Quando "Bruno" envia "o que está faltando?"
    Então "Bruno" recebe uma lista contendo "Arroz" e "Leite"
    E a lista não contém "Café"

  Cenário: Consultar lista vazia
    Dado que não há itens pendentes na lista
    Quando "Bruno" envia "o que está faltando?"
    Então "Bruno" recebe uma resposta informando que não falta nada

  Cenário: Marcar item específico como comprado
    Dado que os itens "Arroz" e "Leite" estão pendentes
    Quando "Bruno" envia "comprei o arroz"
    Então o item "Arroz" fica com status comprado, registrado por "Bruno"
    E o item "Leite" continua pendente
    E nenhum lançamento financeiro é criado

  Cenário: Item mencionado não existe na lista
    Dado que apenas o item "Arroz" está pendente
    Quando "Bruno" envia "comprei o feijão"
    Então nenhum item é marcado como comprado
    E "Bruno" recebe uma pergunta oferecendo registrar "Feijão" como comprado

  # ADR-0030. Até 2026-09-16 a consulta e o índice único comparavam por
  # `lower(name)`, então este caso inseria um segundo item — sem pergunta e sem
  # aviso, que é o pior desfecho possível dos dois.
  @saneamento
  Cenário: Item escrito sem acento é reconhecido, não duplicado
    Dado que o item "Café" já está pendente na lista
    Quando "Bruno" envia "acabou cafe"
    Então a lista tem exatamente um item pendente
    E a lista continua com um único item "Café" pendente
    E "Bruno" é informado de que o item já estava na lista, pedido por "Ana"

  Cenário: Reentrega da mesma mensagem pelo provedor
    Quando o provedor entrega duas vezes a mesma mensagem "acabou o arroz" de "Ana"
    Então o item "Arroz" entra na lista de compras uma única vez
    E "Ana" recebe exatamente um recibo

  # ADR-0039, decisão aberta #25. Estes três cenários são da Etapa 3 e levam tag
  # própria, embora morem num arquivo @etapa2: cenário novo em arquivo de etapa
  # fechada herda a tag da Funcionalidade, e sem o @etapa3 aqui eles pintariam
  # de vermelho o portão da Etapa 2a, que está verde e deve continuar.
  # `Etapa2AcceptanceTest` exclui @etapa3 pelo mesmo motivo que já excluía
  # @saneamento.
  #
  # Até 2026-09-19 "remover chocolate" voltava do modelo como
  # marcarItemComprado com confiança 0,9 -- o item pedido para sair da lista
  # ficava com o status que a Etapa 3 transforma em despesa.
  @etapa3
  Cenário: Remover item que a família desistiu de comprar
    Dado que os itens "Arroz" e "Chocolate" estão pendentes
    Quando "Bruno" envia "remover chocolate"
    Então o item "Chocolate" sai da lista de pendentes
    E o item "Arroz" continua pendente
    E nenhum item é marcado como comprado
    E nenhum lançamento financeiro é criado
    E "Bruno" recebe um recibo confirmando que o item saiu da lista

  @etapa3
  Cenário: Remover item que não está na lista
    Dado que apenas o item "Arroz" está pendente
    Quando "Bruno" envia "remover feijão"
    Então nenhum item sai da lista
    E "Bruno" é informado de que "Feijão" não está na lista

  # O item removido não volta por `desfazer` (ADR-0039): não há lançamento, e o
  # alvo do desfazer é lançamento (ADR-0025). O caminho de volta é avisar de
  # novo, e ele funciona porque o índice único da ADR-0030 só abrange PENDING.
  @etapa3
  Cenário: Item removido pode ser pedido de novo
    Dado que os itens "Arroz" e "Chocolate" estão pendentes
    E que "Bruno" removeu o item "Chocolate" da lista
    Quando "Ana" envia "acabou o chocolate"
    Então a lista continua com um único item "Chocolate" pendente
    E "Ana" recebe um recibo confirmando o item "Chocolate"

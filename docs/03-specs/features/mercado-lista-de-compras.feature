# language: pt

# Tag no nivel da Funcionalidade: os nove cenarios daqui sao todos da mesma
# etapa, entao a tag e herdada em vez de repetida linha a linha. Diferente de
# financas e vinculo, que sao mistos e precisam de tag por cenario.
#
# Deixou de ser verdade: o arquivo tem hoje cenarios @saneamento e @etapa3, que
# herdam o @etapa2 daqui e sao excluidos do portao da Etapa 2a pelo runner
# (Etapa2AcceptanceTest). A contagem atual fica em 03-specs/README.md.
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

  # Uso real, 2026-10-20: "Comprei ovos e sal" voltou do modelo como DUAS
  # chamadas de marcarItemComprado, uma por item, ambas com confiança 1 -- e só
  # a primeira era executada. O sal continuava na lista, o recibo citava só os
  # ovos, e nada avisava que o segundo item tinha ficado para trás. A mesma
  # coisa com "Já comprei / Filtro café / Colorau". Marcar recebia um item só
  # desde a Etapa 2a, e remover desde que nasceu, na Etapa 3; adicionar sempre
  # recebeu a lista.
  #
  # @saneamento, e não @etapa2 nem @etapa3: é uso real depois de as duas etapas
  # estarem fechadas (03-specs/README.md, "Sobre as tags").
  @saneamento
  Cenário: Marcar vários itens como comprados em uma mensagem
    Dado que os itens "Ovos", "Sal" e "Café" estão pendentes
    Quando "Bruno" envia "comprei ovos e sal"
    Então os itens "Ovos" e "Sal" ficam com status comprado, registrados por "Bruno"
    E o item "Café" continua pendente
    E nenhum lançamento financeiro é criado
    E "Bruno" recebe um único recibo citando "Ovos" e "Sal"

  @saneamento
  Cenário: Remover vários itens em uma mensagem
    Dado que os itens "Arroz", "Chocolate" e "Nescau" estão pendentes
    Quando "Bruno" envia "remover chocolate e nescau"
    Então os itens "Chocolate" e "Nescau" saem da lista de pendentes
    E o item "Arroz" continua pendente
    E nenhum item é marcado como comprado
    E "Bruno" recebe um único recibo citando "Chocolate" e "Nescau"

  # O item que está na lista é marcado mesmo assim: o que não estava não
  # bloqueia o que estava. O que não estava recebe a mesma oferta do cenário
  # "Item mencionado não existe na lista", na mesma resposta -- uma pergunta só.
  @saneamento
  Cenário: Um dos itens comprados não está na lista
    Dado que apenas o item "Ovos" está pendente
    Quando "Bruno" envia "comprei ovos e feijão"
    Então o item "Ovos" fica com status comprado, registrado por "Bruno"
    E "Bruno" recebe uma pergunta oferecendo registrar "Feijão" como comprado
    E "Bruno" recebe um único recibo citando "Ovos" e "Feijão"

  # Remover o que não está na lista não vira pergunta (ADR-0039), nem quando
  # vem junto de um item que está.
  @saneamento
  Cenário: Um dos itens a remover não está na lista
    Dado que os itens "Arroz" e "Chocolate" estão pendentes
    Quando "Bruno" envia "remover chocolate e feijão"
    Então o item "Chocolate" sai da lista de pendentes
    E o item "Arroz" continua pendente
    E "Bruno" é informado de que "Feijão" não está na lista
    E "Bruno" recebe um único recibo citando "Chocolate" e "Feijão"

  # Confiança média não executa (ADR-0029), e a pendência tem de guardar a
  # lista inteira: um "sim" que baixasse só o primeiro item seria o mesmo
  # defeito, escondido atrás de uma pergunta.
  @saneamento
  Cenário: Confirmar vários itens comprados quando a interpretação é incerta
    Dado que os itens "Ovos", "Sal" e "Café" estão pendentes
    Quando "Bruno" envia "acho que comprei ovos e sal"
    Então nenhum item é marcado como comprado
    E "Bruno" recebe uma pergunta de confirmação citando "Ovos" e "Sal"
    Quando "Bruno" responde "sim"
    Então os itens "Ovos" e "Sal" ficam com status comprado, registrados por "Bruno"
    E o item "Café" continua pendente

# language: pt
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

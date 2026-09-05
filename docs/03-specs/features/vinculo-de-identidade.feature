# language: pt
Funcionalidade: Vínculo de identidade (onboarding)
  Como pessoa que manda mensagem pela primeira vez
  Quero criar uma família nova ou entrar numa existente por convite
  Para começar a usar a aplicação sem precisar de cadastro fora do chat

  # Cobre dois caminhos, que nunca se misturam:
  # 1) primeiro contato de número desconhecido, sem convite -> só pode criar
  #    a própria família (nunca entra em família de outro por conta própria).
  # 2) número convidado por um OWNER -> só entra na família do convite,
  #    nunca cria a própria.
  # Ver ADR-0020 e ADR-0007 (pessoa em múltiplos households).

  Contexto:
    Dado que o número da aplicação nunca recebeu mensagem de "+5511900000001"

  Cenário: Primeiro contato de número totalmente desconhecido
    Quando "+5511900000001" envia "/start" para o número da aplicação
    Então a pessoa recebe uma breve explicação do que é a aplicação
    E a pessoa recebe uma pergunta perguntando se quer criar uma família nova
    E nenhum household é criado ainda

  Cenário: Aceita criar família nova
    Dado que "+5511900000001" recebeu a pergunta para criar uma família nova
    Quando a pessoa responde "sim, quero criar"
    E a pessoa responde "Silva" para o nome da família
    Então um household "Silva" é criado
    E a pessoa vira membro desse household com papel "OWNER"
    E o "channel_identity" da pessoa fica com "active_household_id" apontando pro household "Silva"
    E o household "Silva" ganha uma conta do tipo carteira implícita
    E a pessoa recebe uma pergunta se prefere continuar tudo pelo chat ou terminar a configuração no aplicativo

  Cenário: Escolhe terminar configuração no aplicativo, antes da Etapa 4 existir
    Dado que "+5511900000001" acabou de criar o household "Silva"
    Quando a pessoa responde "prefiro pelo app"
    Então a pessoa recebe um aviso de que o aplicativo web ainda não está disponível nesta etapa
    E a pessoa recebe a opção de continuar a configuração pelo chat

  Cenário: OWNER convida um novo membro
    Dado que existe o household "Silva" com "Ana" como "OWNER"
    Quando "Ana" envia "convidar Bruno, +5511900000002"
    Então um convite é criado para o telefone "+5511900000002" com status "PENDING"
    E o convite expira em 7 dias
    E "Ana" recebe o link do convite
    E o sistema não envia o link para "Bruno" — quem repassa é "Ana"

  Cenário: Convidado aceita dentro do prazo com o telefone certo
    Dado que existe um convite "PENDING" do household "Silva" para o telefone "+5511900000002"
    Quando "+5511900000002" abre o link do convite e envia "/start"
    Então a pessoa recebe um pedido para compartilhar o contato
    Quando a pessoa compartilha o contato "+5511900000002"
    Então a pessoa vira membro do household "Silva" com papel "MEMBER"
    E o convite fica com status "ACCEPTED"
    E o "channel_identity" da pessoa fica com "active_household_id" apontando pro household "Silva"
    E a pessoa recebe a confirmação de entrada na família

  Cenário: Telefone compartilhado não bate com o convite
    Dado que existe um convite "PENDING" do household "Silva" para o telefone "+5511900000002"
    Quando "+5511900000003" abre o link do convite e compartilha o contato "+5511900000003"
    Então a pessoa recebe um aviso de que este convite não é para o número dela
    E o convite continua com status "PENDING"
    E nenhum vínculo é criado

  Cenário: Convite expirado
    Dado que existe um convite do household "Silva" para o telefone "+5511900000002" criado há 8 dias
    Quando "+5511900000002" abre o link do convite e compartilha o contato "+5511900000002"
    Então a pessoa recebe um aviso de que o convite expirou
    E o convite tem status "EXPIRED"
    E nenhum vínculo é criado

  Cenário: Convite já aceito não pode ser aceito de novo
    Dado que existe um convite "ACCEPTED" do household "Silva" para o telefone "+5511900000002"
    Quando "+5511900000002" abre o link do convite novamente
    Então a pessoa recebe um aviso de que o convite já foi usado
    E nenhum vínculo novo é criado

  Cenário: Número sem convite não entra em família existente por conta própria
    Dado que existe o household "Silva"
    E que "+5511900000004" nunca recebeu convite de nenhum household
    Quando "+5511900000004" envia "quero entrar na família do Silva"
    Então a pessoa recebe um aviso de que só é possível entrar mediante convite
    E a pessoa recebe a opção de criar a própria família em vez disso

  Cenário: Pessoa que já é membro de outra família aceita um convite novo
    Dado que "Carla" já é membro do household "Costa" com o telefone "+5511900000005"
    E que existe um convite "PENDING" do household "Silva" para o telefone "+5511900000005"
    Quando "+5511900000005" abre o link do convite e compartilha o contato "+5511900000005"
    Então "Carla" vira membro do household "Silva" também, reaproveitando a mesma pessoa
    E o "active_household_id" de "Carla" continua apontando pro household "Costa"
    E "Carla" recebe a confirmação de entrada na família "Silva", com a informação de como trocar de família ativa

# Roteiro de teste no Android físico

Use um aparelho de teste com **Mock GPS** instalado e localização ativada. A API do Android mantém essas posições marcadas como fictícias. Não deixe a simulação ativa após os testes.

## Preparação

- [ ] Instale o APK seguindo o [README](../README.md#instalar-no-samsung-galaxy-a34).
- [ ] Conceda as permissões apresentadas pelo app.
- [ ] Selecione **Mock GPS** em **Opções do desenvolvedor → Selecionar app de local fictício**.
- [ ] Abra um app de teste que mostre a posição recebida e permita atualizar a leitura.

## Fluxos

- [ ] **Autorização ausente:** desmarque temporariamente Mock GPS nas Opções do desenvolvedor, volte ao app e toque em **Aplicar**. O app deve mostrar **Permissão necessária** e oferecer a abertura das Opções do desenvolvedor. Selecione Mock GPS novamente antes de continuar.
- [ ] **Validação e mapa:** informe `-3,7319` e `-38,5267`; confirme que a vírgula é aceita. Toque em outro ponto no mapa e confira se os campos e o marcador mudam juntos. Tente latitude `91` e confirme a mensagem de intervalo.
- [ ] **Iniciar e atualizar:** aplique `-3.7319`, `-38.5267`; confirme o estado **Ativo**, a notificação persistente e a posição no app de teste. Altere as coordenadas enquanto ativo e aplique novamente; confirme que a nova posição aparece sem encerrar o serviço.
- [ ] **Variação:** desligue **Pequenas variações de coordenadas**, aplique e confira que o ponto publicado é o centro selecionado. Ligue a opção, aplique e observe atualizações suaves dentro do limite de 5 m, sem afastamento contínuo do centro.
- [ ] **Tela bloqueada:** com a simulação ativa, bloqueie a tela por pelo menos um minuto. Desbloqueie e confirme que o serviço e a notificação continuam ativos e que o app de teste ainda recebe atualizações.
- [ ] **Parada pela notificação:** use **Parar** na notificação. Ela deve desaparecer, o app deve mostrar **Inativo** e o app de teste deve deixar de receber as posições simuladas.
- [ ] **Parada pelo app e repetição:** inicie e pare pelo app pelo menos cinco vezes. Confirme que cada início funciona e que a notificação some após cada parada.
- [ ] **Falha e recuperação:** enquanto ativo, desmarque Mock GPS nas Opções do desenvolvedor. O app deve encerrar a simulação e mostrar erro se o Android revogar o provedor. Selecione Mock GPS novamente, toque em **Aplicar** e confirme que um novo ciclo funciona.
- [ ] **Sem rede:** ative o modo avião, informe as coordenadas manualmente e confirme que os controles permanecem utilizáveis mesmo sem carregar o mapa. Encerre o teste e restaure a conectividade.

## Resultado

Registre a versão do Android/One UI e o resultado de cada item. Este roteiro ainda não foi executado em um aparelho físico; não marque itens como aprovados sem verificá-los no dispositivo.

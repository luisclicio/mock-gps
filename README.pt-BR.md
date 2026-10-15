# Mock GPS

[English](README.md) | [Português (Brasil)](README.pt-BR.md)

Aplicativo Android nativo para testes locais de localização simulada. Permite informar latitude/longitude, escolher um ponto no mapa, ativar variações suaves de até 5 metros e manter atualizações por um serviço em primeiro plano.

O projeto usa Kotlin, Android Gradle Plugin 8.11.1, Gradle 8.13, Kotlin 2.2.21, Android SDK 36 e MapLibre Native Android 11.8.0. O Docker fornece JDK e Android SDK; no computador host são necessários Docker Compose e, somente para instalar no aparelho, Android Platform Tools (`adb`). O app usa inglês por padrão e muda para português quando o idioma do dispositivo está configurado como português.

## Construir e testar

Na raiz do repositório, construa a imagem uma vez:

```sh
docker compose build
```

Depois, construa o APK e execute os testes unitários e o lint:

```sh
./scripts/build.sh
./scripts/test.sh
```

Os comandos equivalentes são:

```sh
docker compose run --rm android ./gradlew assembleDebug
docker compose run --rm android ./gradlew testDebugUnitTest
docker compose run --rm android ./gradlew lintDebug
```

O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`. O volume Docker `gradle-cache` mantém o cache de dependências e a chave de assinatura de debug entre execuções. Preserve esse volume para que novas versões do APK possam atualizar a instalação existente. Se ele for removido, o Gradle cria outra chave e o Android exige desinstalar a versão anterior antes de instalar a nova. O código-fonte é montado no container a partir da raiz do repositório e fica disponível durante o build. Na primeira execução, o script baixa o Gradle Wrapper do repositório oficial do projeto e verifica o SHA-256 fixado no repositório; o Gradle também verifica o checksum da distribuição. O build da imagem baixa as ferramentas Android fixadas no `Dockerfile`.

Para trocar o estilo do mapa, defina `mapStyleUrl` na linha de comando do Gradle, por exemplo:

```sh
docker compose run --rm android ./gradlew assembleDebug -PmapStyleUrl=https://tiles.openfreemap.org/styles/liberty
```

## GitHub Actions e releases

Pull requests para `main` e pushes para `main` executam os testes unitários, o lint e o build de depuração em Docker. Depois de um push bem-sucedido para `main`, a pipeline também gera um APK de release assinado e publica uma GitHub Release chamada `v1.0.<número-da-execução>`, com o APK anexado. O `versionCode` usa o número da execução para que cada release possa atualizar a anterior.

Para manter a mesma assinatura em todas as versões, crie uma chave de release e guarde uma cópia segura fora do repositório. O diretório `.secrets/` já é ignorado pelo Git. Na raiz do projeto, gere uma chave uma única vez:

```sh
docker compose build android
mkdir -p .secrets
docker compose run --rm --user "$(id -u):$(id -g)" \
  --volume "$PWD/.secrets:/secrets" android \
  keytool -genkeypair -v -storetype JKS \
    -keystore /secrets/mock-gps-release.jks \
    -alias mockgps -keyalg RSA -keysize 2048 -validity 10000
```

No repositório GitHub, abra **Settings → Secrets and variables → Actions** e cadastre estes repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: conteúdo de `.secrets/mock-gps-release.jks` codificado em uma linha com `base64 .secrets/mock-gps-release.jks | tr -d '\n'`.
- `ANDROID_SIGNING_STORE_PASSWORD`: senha do keystore.
- `ANDROID_SIGNING_KEY_ALIAS`: `mockgps`.
- `ANDROID_SIGNING_KEY_PASSWORD`: senha da chave.

Não compartilhe a chave nem as senhas e não as adicione ao Git. A perda da chave impede que o Android aceite atualizações assinadas com uma nova chave. Instalações de depuração existentes usam outra assinatura; para instalar a primeira release sobre uma delas, será necessário desinstalá-la uma vez, o que apaga os dados locais. Releases futuras manterão a assinatura e poderão atualizar a instalação normalmente. O job de publicação usa `GITHUB_TOKEN` com permissão `contents: write`; se uma política da organização bloquear essa permissão, habilite escrita de workflows nas configurações do repositório.

## Instalar em um dispositivo Android

Se `adb install -r` retornar `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, o APK já instalado foi assinado com outra chave. Anote as coordenadas selecionadas e as preferências do app, remova a instalação antiga uma vez com `adb uninstall dev.local.mockgps` e instale o APK novamente. Essa remoção apaga os dados locais do app. Builds posteriores reutilizam a chave no volume `gradle-cache` e podem atualizar a instalação normalmente.

1. Ative as Opções do desenvolvedor nas configurações do dispositivo. O caminho e o nome do item **Número da versão** variam conforme o fabricante e a versão do Android; normalmente é necessário tocar nele sete vezes. Confirme o bloqueio do aparelho quando solicitado.
2. Em **Opções do desenvolvedor**, ative **Depuração USB** e conecte o dispositivo ao computador. Autorize a chave RSA na tela do aparelho.
3. Construa o APK e instale usando o `adb` do host:

   ```sh
   ./scripts/build.sh
   ./scripts/install.sh
   ```

   Ou use diretamente `adb install -r app/build/outputs/apk/debug/app-debug.apk`. O ADB permanece no host; não é necessário encaminhar USB ao Docker.
4. Abra **Mock GPS**, conceda a permissão de localização e notificações quando solicitadas e habilite a localização do aparelho.
5. Em **Opções do desenvolvedor → Selecionar app de local fictício**, escolha **Mock GPS**. O caminho e os nomes dos menus podem variar conforme o fabricante e a versão do Android.
6. Informe coordenadas ou toque no mapa e pressione **Aplicar**. Para encerrar, pressione **Parar** no app ou na notificação persistente.

O aplicativo não precisa de root. O Android mantém a simulação marcada como localização fictícia; o app não tenta esconder nem alterar esse sinal.

## Privacidade e limitações

- As coordenadas selecionadas e preferências ficam em `SharedPreferences` local. O app não tem conta, analytics, anúncios, backend ou banco remoto e não lê a localização real do aparelho.
- A permissão de localização é solicitada porque o Android a exige para este serviço em primeiro plano. Em Android 8–11 também é necessária a permissão precisa para registrar o provedor de teste; o manifesto limita essa permissão a essas versões. Ela não é usada para obter a localização do usuário.
- A permissão de internet serve para carregar estilo, fontes e tiles do mapa. O provedor padrão é o [OpenFreeMap](https://openfreemap.org/quick_start/), que não exige chave. Ao mostrar uma região, as requisições de tiles revelam ao provedor a área do mapa consultada; por isso, não use o mapa se não quiser fazer essas requisições. A entrada manual continua disponível offline.
- O provedor público de mapa é oferecido “como está”; [termos do OpenFreeMap](https://openfreemap.org/tos/) e disponibilidade podem mudar. Atribuição do mapa permanece visível. Para produção ou tráfego elevado, configure um provedor apropriado respeitando seus termos e limites.
- A API oficial de provedores de teste é usada. Apps podem detectar a simulação por `Location.isMock()`, e alguns apps usam provedores de localização fundida ou políticas próprias, portanto o resultado pode variar por aparelho e aplicativo.
- As variações são um passeio aleatório suave limitado a 5 m, atualizado a cada segundo. Para cumprir esse intervalo com a tela bloqueada, o serviço mantém um bloqueio parcial de CPU enquanto estiver ativo, o que consome bateria. Ele libera o bloqueio ao parar e não reinicia a simulação após reinicialização do aparelho.
- A seleção inicial do mock provider, o serviço em primeiro plano, a notificação e a integração com provedores de cada fabricante precisam ser conferidos no aparelho. Não há resultado de teste físico documentado como aprovado neste repositório.

## Desenvolvimento

O app usa Views XML e separa a tela (`MainActivity`), integração com `LocationManager` (`MockLocationController`), execução em segundo plano (`MockLocationService`), validação e variação de coordenadas e interação do mapa. O mapa é implementado com MapLibre Native Android e estilo OpenFreeMap Liberty. Os valores de latitude/longitude aceitam ponto ou vírgula decimal.

Testes unitários cobrem os limites de entrada, NaN/infinito, variação desativada, limites de distância, movimento determinístico, polos e a troca da linha internacional de data. Os fluxos de permissão, serviço, notificação e provedor de teste ainda exigem validação manual em um Android físico.

Siga o [roteiro de testes em português](docs/device-test-checklist.pt-BR.md) ou consulte o [roteiro de testes em inglês](docs/device-test-checklist.md) para verificar esses fluxos e registrar resultados sem confundir instruções com testes já executados.

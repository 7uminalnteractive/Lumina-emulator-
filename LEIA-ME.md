# GMP Gameport — Fase 2 (funcional): itens 3, 4, 5, 6 e 7

Este zip contém **apenas os arquivos alterados ou novos**, na mesma estrutura
de pastas do seu repositório. Extraia por cima da pasta raiz do projeto
(substituindo os arquivos existentes) e faça o commit normalmente.

Nenhum arquivo fora desta lista foi tocado.

## O que mudou em cada item do prompt

### Item 3 — Nome do usuário vindo da conta
- `android/res/layout/activity_login.xml` — novo campo "Nome" no cadastro
  (antes só existia e-mail/senha; o nome era adivinhado do e-mail).
- `android/src/.../LoginActivity.java`, `AuthClient.java` — leem e usam esse
  nome real ao criar a conta.
- `android/src/.../LibraryActivity.java`, `StoreActivity.java`,
  `AccountActivity.java` — agora leem `AccountStore.getActiveAccount()` e
  mostram a inicial certa no avatar (antes era "G" fixo nas três).
- `android/res/layout/activity_library.xml`, `activity_store.xml`,
  `activity_account.xml` — adicionados `id`s (`profile_avatar`,
  `profile_status_dot`) para o Java conseguir atualizar essas views.
- `android/src/.../ProfileBadgeHelper.java` (novo arquivo) — centraliza a
  lógica de preencher avatar/status nas 3 telas.

### Item 4 — Status online real e persistido
- `android/src/.../LocalAccount.java` — novo campo `online` (default `true`).
- `android/src/.../AccountStore.java` — persiste/lê esse campo em JSON, e
  novo método `setAccountOnline(id, online)` pronto para uso futuro com
  presença real via backend.
- `android/res/drawable/lumina_offline_dot.xml` (novo arquivo) — par cinza do
  ponto verde que já existia, para refletir status offline.

### Item 5 — Vulkan + PSPx8 (4K) como padrão gráfico
- `Core/Config.cpp` — `DefaultGPUBackend()` agora prefere Vulkan em mais
  versões de Android (mantendo a blacklist de dispositivos problemáticos
  intacta); `DefaultInternalResolution()` retorna 8 (PSPx8) em Android.
- Isso só afeta contas/perfis **novos** — quem já tem config salva não é
  alterado.

### Item 6 — Bug do jogo reiniciando ao voltar do segundo plano
- `android/AndroidManifest.xml` — `configChanges` da `PpssppActivity` agora
  inclui `orientation|screenSize|screenLayout|smallestScreenSize`, evitando
  que a Activity seja destruída/recriada por mudança de configuração.

### Item 7 — Autosave dedicado (slot -100)
Fluxo: `PpssppActivity.onPause()` chama `NativeApp.saveStateForBackground()`
**antes** de pausar a superfície gráfica (necessário porque salvar um estado
tira um screenshot do frame atual). Essa chamada roda na UI thread do
Android, mas o estado do emulador só pode ser tocado pela thread principal
do motor — então ela despacha o trabalho com `System_RunOnMainThread` e
espera (bloqueando, com timeout de 500ms) até terminar.

- `android/src/.../NativeApp.java` — novo método nativo `saveStateForBackground()`.
- `android/src/.../PpssppActivity.java` — chama esse método no início de `onPause()`.
- `android/jni/app-android.cpp` — implementação JNI bloqueante com timeout.
- `Common/System/NativeApp.h`, `UI/NativeApp.cpp` — função ponte
  `GMP_SaveEmulatorStateForBackground()` que acessa a `EmuScreen` atual.
- `Common/System/System.h` — sem mudanças funcionais nesta versão (a
  primeira tentativa usava uma `UIMessage` nova, mas foi abandonada por não
  ser confiável — ver observação abaixo).
- `UI/EmuScreen.h`, `UI/EmuScreen.cpp` — `AutoSaveOnBackground()` (salva no
  slot `-100`, dedicado, que nunca colide com os slots normais 0-4) e
  `AutoLoadBackgroundSaveIfPresent()` (restaura esse slot no boot seguinte,
  com prioridade sobre o autoload normal, e o apaga depois de usado).

## Limitação honesta que continua existindo

Mesmo com o timeout de 500ms, ainda existe uma janela mínima em que o
Android pode matar o processo tão rápido que o autosave não termina a
tempo. Isso é uma limitação real da plataforma, não um bug do código — o
próprio pedido original já reconhecia isso ("não é necessário prometer que
o processo nunca será encerrado pelo sistema").

## Não testado por compilação real

Não tenho ambiente com Android SDK/NDK aqui, então tudo foi revisado
manualmente (includes, assinaturas de função, balanceamento de
chaves/parênteses, mas **não compilado de fato**. É esperado que o
`build.yml` no GitHub Actions seja quem valida isso de verdade — se der erro,
me manda o log que eu corrijo.

## Ainda não implementado

Fase 3 do prompt original (redesign visual das 5 telas: sidebar, Biblioteca,
login, Save State, configurações do PSP) — combinamos de fazer essa parte
depois de validar a Fase 2 compilando.

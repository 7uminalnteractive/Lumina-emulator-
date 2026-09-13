# GMP Gameport — Reorganização de pastas (PSP → GMP, 11 → 3 pastas)

## Como aplicar

**Substituir** (mesmo caminho, mesmo nome) — todos os 16 arquivos deste
pacote substituem os originais nesses caminhos:

- `Core/Util/PathUtil.cpp`
- `Core/Util/MemStick.cpp`
- `Core/HLE/sceIo.cpp`
- `Core/FileSystems/DirectoryFileSystem.cpp`
- `Core/Loaders.cpp`
- `Core/PSPLoaders.cpp`
- `Core/System.cpp`
- `Core/Config.cpp`
- `UI/CwCheatScreen.cpp`
- `UI/GameInfoCache.cpp`
- `UI/GameScreen.cpp`
- `UI/GameSettingsScreen.cpp`
- `UI/NativeApp.cpp`
- `UI/ImDebugger/ImDebugger.cpp`
- `android/src/org/ppsspp/ppsspp/LibraryActivity.java`
- `android/res/layout/activity_library.xml`

Nenhum arquivo novo, nenhum arquivo para remover nesta mudança.

## ⚠️ Sobre o risco desta mudança

Você pediu explicitamente para seguir com o caminho de maior risco, então
aqui vai o registro do que isso significa: os nomes de pasta do PSP
(`PSP/GAME`, `PSP/SAVEDATA`, `PSP/TEXTURES` etc.) não são só organização
de arquivos -- são o padrão real de hardware do PSP, e o emulador simula
a API de sistema de arquivos verdadeira do console usando exatamente
esses nomes. **Alguns jogos ou homebrews que checam esses nomes de forma
muito específica podem se comportar de forma diferente ou parar de
funcionar corretamente.** Fiz o possível para reduzir esse risco (ver
"Rede de segurança" abaixo), mas não há como eliminá-lo por completo sem
poder compilar e testar com uma biblioteca real de jogos.

## Nova estrutura

```
GMP/                    (raiz, antes chamada "PSP")
├── Jogo/
│   ├── Game/            (jogos -- antes PSP/GAME)
│   └── Save/            (save data -- antes PSP/SAVEDATA)
├── Textura/             (texturas customizadas -- antes PSP/TEXTURES)
└── Sistema/             (tudo o mais, cada um mantendo seu nome interno)
    ├── Cheats/
    ├── SYSTEM/
    ├── SCREENSHOT/
    ├── PPSSPP_STATE/    (save states)
    ├── PLUGINS/
    ├── VIDEO/
    ├── AUDIO/
    ├── shaders/
    ├── themes/
    └── SYSTEM/CACHE, SYSTEM/DUMP
```

A Biblioteca do app Android (`LibraryActivity`) agora escaneia
`Armazenamento/GMP/Jogo/Game`, batendo com o que o engine C++ cria e usa.

## Rede de segurança: compatibilidade com a estrutura antiga

Em todo lugar do código que checava literalmente pela string
`"PSP/GAME/"` para decidir algum comportamento (detectar homebrew, gerar
ID de jogo falso, evitar gravar certas pastas em replays, etc.), a
correção **verifica os dois padrões**: primeiro tenta o novo
(`GMP/Jogo/Game/`), e se não encontrar, cai para o antigo (`PSP/GAME/`)
como fallback. Isso cobre o caso de alguém restaurar um backup antigo ou
copiar uma pasta de jogos já organizada à moda antiga -- ela continua
funcionando, só que o app não vai *criar* mais pastas nesse formato para
frente.

Isso foi aplicado em: `Core/Loaders.cpp`, `Core/PSPLoaders.cpp`,
`Core/System.cpp`, `Core/Util/MemStick.cpp`, `UI/CwCheatScreen.cpp`,
`UI/GameInfoCache.cpp`, `UI/GameScreen.cpp`, e duas ocorrências em
`Core/FileSystems/DirectoryFileSystem.cpp`.

## Onde NÃO houve fallback duplo (e por quê)

- **`Core/Util/PathUtil.cpp`** (`GetSysDirectory`): esta função define
  para onde o emulador escreve dados NOVOS -- não tem "modo de
  compatibilidade", ela sempre aponta para a estrutura nova. É o
  contrato central de toda a mudança.
- **`Core/FileSystems/DirectoryFileSystem.cpp`** (`STRIP_PSP`, listagem
  artificial de diretório raiz): esta é a tradução entre o caminho que
  o jogo *dentro do emulador* enxerga (`ms0:/...`) e o caminho físico
  real. Ela sempre usa "GMP" agora, para bater com o que `PathUtil.cpp`
  cria.
- **`Core/HLE/sceIo.cpp`** (blacklist de pastas visíveis ao jogo): a
  lista de nomes escondidos foi atualizada para os novos nomes de topo
  ("Sistema", "Textura"). A checagem especial que esconde uma pasta
  "GAME" solta na raiz do cartão (comentário original menciona que isso
  existe por causa do jogo Wipeout) foi mantida exatamente como estava,
  pois é sobre um caso diferente (compatibilidade com estrutura de
  memory stick real do PSP, não relacionado à reorganização interna).

## O que é só texto (não foi tratado com fallback, pois não afeta
## comportamento, só o que aparece na tela)

- `UI/GameSettingsScreen.cpp`: texto de ajuda mostrando o caminho USB
  para o usuário (`/PSP` → `/GMP`).
- `UI/ImDebugger/ImDebugger.cpp`: painel de debug interno, só rótulos.
- `UI/CwCheatScreen.cpp` (segunda ocorrência): texto "Import from
  PSP/Cheats/cheat.db" → atualizado para "GMP/Sistema/Cheats/cheat.db".

## Validação feita

- Toda referência a nomes de pasta antigos (`PSP/`, `SAVEDATA`, `GAME`,
  `TEXTURES`, etc.) foi localizada e revisada manualmente em todo o
  código-fonte (fora da pasta `ext/`, que é código de terceiros/bibliotecas
  que não deve ser tocado).
- Cada ocorrência foi lida em contexto antes de decidir se precisava
  mudar -- vários resultados da busca eram falsos positivos (nome de
  categoria de log, nome de dispositivo de input do Libretro, prefixo
  de arquivo temporário do Windows) e foram deixados intocados de
  propósito.
- Todos os 14 arquivos `.cpp`/`.h` editados foram checados quanto ao
  balanceamento de chaves e parênteses, excluindo linhas de comentário
  puro (uma primeira passada acusou 1 parêntese "sobrando" em
  `sceIo.cpp`, que na investigação se mostrou ser só um emoticon `:(`
  dentro de um comentário do código original, não um erro real).
- `LibraryActivity.java` e `activity_library.xml` confirmados
  balanceados/consistentes após a atualização do caminho escaneado.

## O que ainda pode precisar de atenção (não veio neste pacote)

- **Não testei com jogos reais** (não tenho como compilar e rodar aqui).
  A recomendação é testar com pelo menos um jogo comercial e um
  homebrew conhecido por ser sensível a estrutura de pastas antes de
  distribuir para outras pessoas.
- Caso existam **outros lugares no projeto que leem/escrevem caminhos
  de pasta a partir de arquivos de configuração salvos por usuários
  antigos** (ex: um `ppsspp.ini` de instalação anterior do PPSSPP puro,
  não deste fork), o caminho salvo ali pode apontar para a estrutura
  antiga -- isso não foi coberto aqui pois está fora do código-fonte.

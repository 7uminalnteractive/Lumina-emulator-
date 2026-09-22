# GMP Gameport — Reversão do hash, save path, textures.ini central, idioma

Este zip contém **todos os arquivos alterados** nesta sessão. Extraia por
cima da raiz do repositório e commite.

## 1. Hash de textura GMP revertido

Reversão completa (nenhum vestígio de "gmp" restou):
- `GPU/Common/TextureDecoder.h`/`.cpp` — removida a função `GMPStableTexHash`.
- `GPU/Common/ReplacedTexture.h` — removido `ReplacedTextureHash::GMP` do enum.
- `GPU/Common/TextureReplacer.cpp` — removido o parsing de `hash = gmp`, os
  dois `case` correspondentes, e o comentário do `.ini` gerado
  automaticamente voltou ao original.

Volta ao comportamento 100% padrão do PPSSPP: só `quick`, `xxh32`, `xxh64`.

## 2. Save path corrigido: agora vai de verdade para Jogo/Save

**Descoberta:** o código já tinha uma reorganização parcial de pastas
(`GetSysDirectory(DIRECTORY_SAVEDATA)` já retornava `GMP/Jogo/Save`), mas o
sistema de arquivos virtual do PSP que todo jogo usa
(`ms0:/PSP/SAVEDATA/...`, fixo no próprio hardware/API do PSP — não é algo
que dá para renomear sem quebrar o jogo) nunca respeitava essa
reorganização, então os saves continuavam caindo fisicamente em
`PSP/SAVEDATA/` no disco.

- `Core/FileSystems/FileSystem.h` — nova flag `REDIRECT_PSP_SAVEDATA`.
- `Core/FileSystems/DirectoryFileSystem.cpp` — os dois métodos
  `GetLocalPath` agora interceptam qualquer caminho começando com
  `PSP/SAVEDATA` e redirecionam para `Jogo/Save` (ou `GMP/Jogo/Save`,
  dependendo se a raiz de armazenamento escolhida já é a própria pasta
  "GMP" ou não — a mesma lógica que a flag `STRIP_PSP` já usa).
- `Core/HLE/sceIo.cpp` — a nova flag é ativada no mount do `ms0:`.

Cobre tanto o fluxo normal de save/load do jogo (`SavedataParam.cpp`)
quanto a instalação de dados de jogo (`PSPGamedataInstallDialog.cpp`) —
ambos passam pela mesma camada de sistema de arquivos, então nenhum dos
dois precisou ser editado diretamente.

## 3. textures.ini agora fica em Sistema/.TEXTURES/

As imagens de cada pack continuam em `Textura/<jogo>/` como sempre. O
arquivo `textures.ini` em si agora é lido de
`GMP/Sistema/.TEXTURES/<jogo>/textures.ini` (pasta oculta, maiúscula,
como pedido) — um lugar central, separado das imagens.

- `Core/Util/PathUtil.h`/`.cpp` — novo `DIRECTORY_TEXTURE_INIS`,
  resolvendo para `Sistema/.TEXTURES`; adicionado à lista de pastas
  criadas automaticamente no primeiro boot.
- `GPU/Common/TextureReplacer.cpp` — o carregamento do `.ini` agora usa
  `IniFile::Load()` apontando para esse novo caminho central, em vez de
  `LoadFromVFS` na própria pasta do pack. **Sem fallback** para o local
  antigo, como você confirmou: se o `.ini` não estiver no novo lugar, o
  app simplesmente não carrega nenhum `.ini` (mas ainda reconhece imagens
  com nome de hash direto na pasta, se houver). Packs zipados (`.zip`)
  continuam levando o `.ini` junto dentro do zip — não fazia sentido
  separar isso nesse caso.

**Para o pack KMPES que você mandou:** a pasta a criar seria
`GMP/Sistema/.TEXTURES/<ID do jogo>/`, com o `textures.ini` (o mesmo que
já revertemos para `hash = quick`) dentro dela; as imagens do pack
continuam em `GMP/Textura/<ID do jogo>/` como já estavam.

## 4. Idioma: Castellano (España) agora em português

- `assets/lang/es_ES.ini` — conteúdo substituído pelo de `pt_BR.ini`
  (confirmei com diff que ficaram byte-a-byte idênticos). O nome exibido
  na lista de seleção de idiomas continua "Castellano (España)" — isso
  vem de um arquivo totalmente separado (`assets/langregion.ini`, não
  incluído aqui porque não foi alterado), que não toquei.

Resultado: o usuário seleciona "Castellano (España)" na lista (como já
fazia) e todos os textos da interface aparecem em português.

## Não testado por compilação real

Revisão manual feita em tudo (balanceamento de chaves/parênteses,
diff byte-a-byte do arquivo de idioma, verificação de fluxo completo do
save através de duas classes diferentes), mas não compilado de fato aqui
— o `build.yml` no Actions valida isso.

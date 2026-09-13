# GMP Gameport — Correção: criar pastas automaticamente

## Como aplicar

**Substituir** (mesmo caminho):
- `android/src/org/ppsspp/ppsspp/LibraryActivity.java`

## O que estava acontecendo

Quando o usuário concedia a permissão "Acesso a todos os arquivos", nada
de fato criava a pasta `storage/emulated/0/GMP/Jogo/Game`. A tela de
Biblioteca só *verificava* se essa pasta já existia
(`if (root.exists() && root.isDirectory())`) para decidir o que
mostrar -- se ela não existisse, o app simplesmente caía no estado
"nenhum jogo encontrado", sem nunca ter criado nada. Isso deixava a
pessoa sem saber onde colocar os arquivos dos jogos, já que a pasta
nem existia para ela navegar até lá pelo gerenciador de arquivos.

O motivo disso não ter aparecido antes: o motor do emulador (C++) até
tem uma função que cria essa estrutura de pastas automaticamente
(`CreateSysDirectories()`, em `PathUtil.cpp`) -- mas ela só roda quando
o **emulador em si** é iniciado (abrindo um jogo, ou entrando nas
configurações), não quando o usuário só está navegando na tela de
Biblioteca, que é uma tela própria em Java, separada do motor do
emulador. Ou seja: faltava jogo para a pasta ser criada, e faltava a
pasta para colocar o jogo.

## O que foi corrigido

`LibraryActivity.scanGamesFolder()` agora chama um novo método,
`ensureGmpFoldersExist()`, antes de escanear. Esse método:

1. Cria `GMP/Jogo/Game` (a pasta que esta tela escaneia), se ainda não
   existir.
2. Também cria de uma vez as pastas irmãs `GMP/Jogo/Save`,
   `GMP/Textura` e `GMP/Sistema`, para a estrutura já nascer completa
   assim que a permissão é concedida, em vez de ir aparecendo aos
   poucos conforme cada funcionalidade for usada pela primeira vez.

Isso roda dentro da mesma thread em segundo plano que já existia para o
escaneamento (não trava a interface), e qualquer falha ao criar uma
pasta (por exemplo, por causa de alguma restrição do sistema) só gera
um aviso no log -- não derruba o app.

## Validação feita

- Chaves e parênteses do arquivo contados e batendo (54/54 e 208/208)
- Todo `R.id.*` que o arquivo usa foi conferido contra
  `activity_library.xml` -- nada foi quebrado

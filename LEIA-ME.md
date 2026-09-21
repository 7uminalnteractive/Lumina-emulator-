# GMP Gameport — Hash próprio para substituição de texturas

Este zip contém **apenas os arquivos alterados**. Extraia por cima da raiz
do repositório e commite.

## O que foi feito

Adicionado um quarto algoritmo de hash de textura, próprio do GMP,
selecionável em qualquer pack de texturas com `hash = gmp` no
`textures.ini` dele — ao lado dos três que já existiam (`quick`, `xxh32`,
`xxh64`), que continuam funcionando exatamente como antes.

### Exemplo real do hash

```
input="abc"                              → hash=146d555f
input="PSP texture data example bytes"   → hash=8b29aaf0
input="" (vazio)                          → hash=474d5001
```

Nome de arquivo final (mesmo formato que já existia, só o hash muda):

```
000000001234567802a6a117.png       ← textura normal
000000001234567802a6a117_1.png     ← mipmap nível 1
```

Os 16 primeiros dígitos vêm do endereço/dimensões da textura (isso não
muda); os últimos 8 dígitos são o hash em si — é aí que o GMP muda de
valor comparado a `quick`/`xxh32`/`xxh64` para o mesmo conteúdo.

## Como funciona por baixo

- `GPU/Common/TextureDecoder.h`/`.cpp`: nova função `GMPStableTexHash`,
  usando FNV-1a de 32 bits com uma seed própria (`0x474D5001`, "GMP" em
  hex + versão). Sem SIMD/otimização por plataforma como o `QUICK`
  existente — mais simples e sem risco de bug específico de arquitetura
  que eu não teria como testar.
- `GPU/Common/ReplacedTexture.h`: novo valor `ReplacedTextureHash::GMP` no
  enum.
- `GPU/Common/TextureReplacer.cpp`: reconhece `hash = gmp` no parsing do
  `.ini`, e usa o novo algoritmo nos dois pontos onde o hash é calculado
  (textura com dados contíguos na memória, e textura com "gaps" — os dois
  precisavam do novo `case`, não só um). O `.ini` gerado automaticamente
  quando alguém cria uma pasta de texturas nova continua com `quick` como
  padrão; só o comentário foi atualizado para mencionar a nova opção.

## Limitação importante (já confirmada com você)

Isso **não** é compatível com nenhum pack de texturas já existente na
internet (todos usam `quick`/`xxh32`/`xxh64`, feitos para o PPSSPP
padrão). Só packs **novos**, criados deliberadamente com `hash = gmp`,
vão usar o algoritmo do GMP — e esses packs não funcionariam em nenhum
outro fork do PPSSPP, só no GMP Gameport.

## Não testado por compilação real do projeto

O algoritmo em si eu compilei e rodei isoladamente (fora do projeto) para
confirmar que os hashes de exemplo acima são reais, não inventados. A
integração com o resto do `TextureReplacer.cpp` foi revisada manualmente
(balanceamento de chaves/parênteses, os dois `switch` corrigidos), mas não
compilada dentro do projeto completo — o `build.yml` no Actions valida
isso.

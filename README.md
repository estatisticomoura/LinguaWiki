# LinguaWiki — dicionários offline e Wiktionary online

LinguaWiki é um aplicativo Android de dicionários sem conta, anúncios,
telemetria ou rastreamento. A interface segue o idioma do aparelho e está
traduzida para português, italiano e inglês.

A versão `0.4.0-prototype` introduz o primeiro pacote monolíngue real,
português–português, baixado sob demanda. O APK continua pequeno: o acervo não
fica preso ao instalador.

## O que funciona

- busca offline incremental por lema e forma flexionada;
- busca sem diacríticos e correção aproximada;
- formas ambíguas preservadas (`fui` encontra `ir` e `ser`);
- definições, exemplos, IPA, etimologia e traduções por acepção;
- botão de conjugação ou declinação;
- pronúncia pelo TTS instalado no Android;
- favoritos e histórico locais, inclusive para verbetes de pacotes externos;
- download retomável, verificação SHA-256, validação SQLite e instalação
  atômica;
- remoção individual de pacotes sem apagar favoritos nem histórico;
- consulta online a 173 edições ativas do Wiktionary;
- sugestões online por prefixo, busca aproximada e variantes com diacríticos;
- temas claro, escuro, do sistema e alto contraste;
- paletas verde, azul e âmbar;
- cinco tamanhos de fonte e três opções de entrelinha.

## Pacote português–português

O primeiro pacote foi criado da extração do Wiktionary em português publicada
por Wiktextract/Kaikki.org em 2 de setembro de 2026.

| Medida | Valor |
|---|---:|
| Download comprimido | 39.590.052 bytes (37,8 MiB) |
| Banco instalado | 120.827.904 bytes (115,2 MiB) |
| Verbetes | 93.848 |
| Acepções | 151.369 |
| Traduções | 417.989 |
| Formas indexadas | 449.746 |

O pacote contém palavras portuguesas explicadas em português. Traduções simples
para outros idiomas, exemplos, IPA, etimologia e flexões são preservados quando
existem na fonte. Registros que representam somente uma forma flexionada são
ligados ao lema e não duplicam uma página inteira.

O arquivo não é versionado no Git porque é grande. Ele deve ser anexado ao
Release `v0.4.0-prototype` com o nome
`linguawiki-pt-pt-2026-09-02.sqlite.gz`; o catálogo incorporado ao APK aponta
para esse endereço versionado.

## Instalar e testar

1. No Release `v0.4.0-prototype`, baixe
   `LinguaWiki-prototype-0.4.0-debug.apk`.
2. Abra o APK no Android e autorize a instalação por essa origem, se solicitado.
3. No LinguaWiki, abra **Configurações → Dicionários offline**.
4. Confira download, tamanho instalado, espaço temporário e espaço livre.
5. Confirme o download de **Português**.
6. Volte à busca, selecione português e experimente:

| Digite | Resultado esperado |
|---|---|
| `poder` | verbetes e acepções de “poder” |
| `pudesse` | forma destacada conduzindo ao lema “poder” |
| `fui` | lemas “ir” e “ser” |
| `coracoes` | “coração”/“corações” mesmo sem diacríticos |
| `fazer` | definições, exemplos, etimologia, traduções e conjugação |

O APK é assinado com uma chave de desenvolvimento. Ele serve para teste direto
e não é a assinatura definitiva de publicação na Play Store.

## Consulta online

Em **Configurações → Edições online**, escolha quais Wiktionaries podem aparecer
na tela online. Somente a edição ativa recebe o texto digitado para gerar
sugestões. O cliente tenta novamente uma vez em falhas transitórias, guarda um
pequeno cache em memória e mostra um botão explícito para repetir a consulta.

O conteúdo da página é exibido diretamente pelo site oficial da edição
selecionada. JavaScript e armazenamento DOM ficam desativados no `WebView`.

## Compilar o aplicativo

Requisitos: JDK 17 e Android SDK com a plataforma 35.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Gerar novamente o pacote

O gerador recebe a extração JSONL compactada e produz SQLite, gzip e manifesto:

```bash
python3 tools/build_dictionary_pack.py \
  pt-extract.jsonl.gz \
  linguawiki-pt-pt-2026-09-02.sqlite \
  --gzip-output linguawiki-pt-pt-2026-09-02.sqlite.gz \
  --manifest pt-pt-2026-09-02.json \
  --pack-id pt-pt \
  --version 2026-09-02 \
  --language pt \
  --edition pt \
  --download-url https://github.com/estatisticomoura/LinguaWiki/releases/download/v0.4.0-prototype/linguawiki-pt-pt-2026-09-02.sqlite.gz \
  --source-url https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz \
  --source-sha256 9c333f933157afa21234d4ffd4a6b8fe6eb61b279f6a5debd1d43b612faa8d16
```

Depois da geração, confira o `PRAGMA integrity_check`, os totais do manifesto e
consultas de regressão antes de publicar. O desenho técnico completo está em
[`docs/OFFLINE-PACKS.md`](docs/OFFLINE-PACKS.md).

## GitHub para iniciantes

O procedimento de commit, Actions e criação do Release está descrito passo a
passo em [`docs/GITHUB.md`](docs/GITHUB.md).

## Licenças

- código do aplicativo: MIT (`LICENSE`);
- amostras internas de demonstração: escritas para o protótipo;
- pacote português: CC BY-SA 4.0, com atribuição aos colaboradores do
  Wiktionary em português e registro das transformações realizadas.

Consulte [`NOTICE-DATA.md`](NOTICE-DATA.md) antes de redistribuir os dados.

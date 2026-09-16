# LinguaWiki — dicionários Wiktionary no Android

LinguaWiki é um aplicativo Android de dicionários offline e consulta online ao
Wiktionary. Não exige conta, não contém anúncios e não envia telemetria. A
interface acompanha o idioma do sistema e está traduzida para português,
italiano e inglês.

## Estado da versão 0.4

Esta versão introduz o primeiro pacote monolíngue real, **português–português**.
Ele é baixado somente quando o usuário solicita, portanto o APK permanece
pequeno.

| Item | Valor medido |
|---|---:|
| APK de teste | 16.628.363 bytes |
| Download do pacote português | 39.590.052 bytes |
| Pacote instalado | 120.827.904 bytes |
| Entradas | 93.848 |
| Acepções | 151.369 |
| Traduções | 417.989 |
| Formas flexionadas | 449.746 |

O pacote contém verbetes em português explicados pelo Wiktionary em português,
além de traduções simples para outros idiomas, exemplos, etimologia, IPA e
flexões quando esses campos existem na fonte. Ele não inclui uma segunda camada
de definições em inglês.

## O que funciona

- instalação e remoção do pacote português em **Configurações**;
- confirmação prévia com tamanho do download, tamanho instalado, pico
  temporário necessário e espaço livre no aparelho;
- download retomável, validação SHA-256, descompressão, verificação SQLite e
  ativação segura do pacote;
- busca offline incremental por lema e forma flexionada;
- recuperação sem diacríticos (`coracoes` encontra `coração`);
- resolução de formas ambíguas (`fui` oferece `ir` e `ser`);
- painel destacado “Forma pesquisada → Entrada principal”;
- definições, exemplos, etimologia, IPA, traduções e tabelas de formas;
- pronúncia pelo sintetizador de voz do Android;
- favoritos e histórico armazenados somente no aparelho;
- consulta online às edições do Wiktionary escolhidas pelo usuário;
- sugestões online por prefixo e uma segunda busca aproximada quando necessário;
- erro visível e botão para repetir quando as sugestões online falham;
- temas claro, escuro, alto contraste claro e alto contraste escuro;
- paletas verde, azul e âmbar;
- tamanhos de fonte de 90% a 150% e três opções de entrelinha.

O APK ainda traz pequenas entradas demonstrativas para permitir um primeiro
contato antes de instalar um pacote completo. Quando o pacote português está
instalado, ele tem prioridade sobre essa amostra.

## Instalar e testar

1. Baixe o APK da Release `v0.4.0-prototype`.
2. No Android, autorize a instalação pelo navegador ou gerenciador de arquivos
   se o sistema solicitar.
3. Abra **LinguaWiki**.
4. Entre em **Configurações → Dicionários offline**.
5. Toque em **Baixar** no cartão de português e confirme os tamanhos exibidos.
6. Depois da instalação, volte à busca offline e experimente:

| Digite | Resultado esperado |
|---|---|
| `poder` | verbete completo do verbo |
| `pudesse` | indicação destacada de que é forma de **poder** |
| `fui` | duas entradas principais: **ir** e **ser** |
| `coracoes` | sugestão **coração** mesmo sem cedilha nem til |
| `fazer` | acepções, exemplos, etimologia, traduções e conjugação |

O APK é uma compilação de desenvolvimento e está assinado com chave de teste.
Ele é apropriado para avaliação, não para publicação na Play Store.

## Consulta online

Em **Configurações → Fontes online**, o usuário escolhe quais edições aparecem
na tela de busca. Ao digitar, somente a edição ativa recebe a consulta de
sugestões. As outras edições habilitadas não recebem o texto até serem
selecionadas.

O catálogo contém 173 edições ativas do Wiktionary. É possível filtrar pelo
código, pelo nome localizado ou pelo nome nativo do idioma.

## Compilar o aplicativo

Requisitos: JDK 17 e Android SDK 35.

```bash
./gradlew testDebugUnitTest assembleDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`. O workflow em
`.github/workflows/android.yml` executa a mesma verificação no GitHub Actions.

## Reproduzir o pacote português

O dump bruto não é processado no telefone nem armazenado neste repositório.
Depois de baixar a extração indicada em `NOTICE-DATA.md`, execute:

```bash
python3 tools/build_dictionary_pack.py \
  pt-extract.jsonl.gz \
  linguawiki-pt-pt-2026-09-02.sqlite \
  --edition pt \
  --language pt \
  --pack-id pt-pt \
  --version 2026-09-02 \
  --source-sha256 9c333f933157afa21234d4ffd4a6b8fe6eb61b279f6a5debd1d43b612faa8d16 \
  --gzip-output linguawiki-pt-pt-2026-09-02.sqlite.gz \
  --manifest pt-pt-2026-09-02.json \
  --download-url https://github.com/estatisticomoura/LinguaWiki/releases/download/v0.4.0-prototype/linguawiki-pt-pt-2026-09-02.sqlite.gz
```

O gerador seleciona apenas registros cujo idioma do verbete é português,
normaliza os campos, substitui registros duplicados de formas por ligações aos
lemas, cria índices, executa `PRAGMA integrity_check` e produz um gzip
reprodutível.

## Código e dados

O código do aplicativo é distribuído sob a licença MIT. O pacote de dicionário
é uma obra de dados separada, redistribuída sob CC BY-SA 4.0 com atribuição e
aviso das transformações. Consulte `NOTICE-DATA.md` antes de redistribuí-lo.

O formato e o ciclo de vida dos pacotes estão em `docs/OFFLINE-PACKS.md`. Um
passo a passo para usar este projeto no GitHub está em `docs/GITHUB.md`.

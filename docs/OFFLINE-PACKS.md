# Pacotes offline do LinguaWiki

## Arquitetura adotada

O APK contém a interface, o mecanismo de busca e uma amostra pequena. Cada
acervo real é um banco SQLite somente leitura, compactado e baixado sob demanda.
O usuário pode instalar ou remover idiomas independentemente; a quantidade de
pacotes é limitada pelo armazenamento do aparelho, não por uma lista fixa no
código.

Cada pacote é monolíngue:

- palavras no idioma escolhido;
- definições na edição correspondente do Wiktionary;
- exemplos, IPA e etimologia quando disponíveis;
- formas, conjugação e declinação;
- traduções simples para outros idiomas presentes naquela edição.

Uma segunda coleção completa em inglês não é duplicada dentro do pacote. Isso
mantém o download previsível. A consulta a outras edições continua disponível
no modo online e, futuramente, camadas de referência poderão ser pacotes
opcionais separados.

## Primeiro pacote medido

O pacote `pt-pt` versão `2026-09-02` tem:

| Campo | Valor |
|---|---:|
| `downloadBytes` | 39.590.052 |
| `installedBytes` | 120.827.904 |
| `entryCount` | 93.848 |
| `senseCount` | 151.369 |
| `translationCount` | 417.989 |
| `formCount` | 449.746 |

Esses valores são medidos no artefato real, não estimados a partir de um dump
bruto.

## Produção fora do celular

O telefone não interpreta dumps XML ou JSONL. A geração acontece antes da
publicação:

1. baixar a extração e conferir seu checksum;
2. selecionar os registros do idioma-alvo;
3. normalizar campos e colapsar páginas exclusivamente flexionadas;
4. produzir um banco SQLite com índices;
5. executar `PRAGMA integrity_check` e buscas de regressão;
6. compactar o banco;
7. produzir o manifesto com tamanhos, totais, URL e SHA-256;
8. publicar arquivo e manifesto em um Release versionado.

O script atual é `tools/build_dictionary_pack.py`.

## Formato do banco

O esquema 1 contém:

- `meta`: versão do esquema, pacote, edição, idioma e versão dos dados;
- `entries`: lema, forma normalizada, forma sem diacríticos, classe, IPA,
  etimologia e tipo de flexão;
- `senses`: definição e lista JSON de exemplos;
- `translations`: idioma e termo ligados à acepção;
- `forms`: superfície, chaves de busca e rótulo morfológico.

Os IDs expostos ao aplicativo têm o formato
`pack/<packId>/<stableId>`. Favoritos e histórico guardam também uma fotografia
dos metadados básicos, portanto não desaparecem quando o pacote é removido.

## Instalação segura

Antes da confirmação, a interface mostra:

- tamanho do download;
- tamanho final instalado;
- espaço temporário máximo;
- espaço livre atual.

O pico exigido é:

```text
downloadBytes + installedBytes + margem de 16 MiB
```

O fluxo implementado é:

1. continuar um arquivo parcial usando HTTP `Range` quando o servidor aceita;
2. restringir origem e redirecionamento final a HTTPS;
3. verificar tamanho e SHA-256 do gzip;
4. descompactar para arquivo temporário;
5. verificar tamanho, metadados e `PRAGMA integrity_check`;
6. renomear a versão anterior para backup;
7. ativar o novo banco por renomeação atômica;
8. restaurar o backup se a ativação falhar;
9. apagar o gzip somente depois do sucesso.

Uma interrupção não substitui um dicionário instalado por um arquivo parcial.

## Busca

A ordem de prioridade é:

1. lema exato;
2. forma exata;
3. lema ou forma equivalentes sem diacríticos;
4. prefixo;
5. distância de edição limitada.

Resultados de formas mostram em destaque a relação entre a superfície digitada
e o lema. Para pacotes muito maiores, a etapa aproximada deve migrar para um
índice de trigramas ou SymSpell, evitando varredura de candidatos por tamanho.

## Pronúncia

O caminho padrão é o sintetizador de voz do Android, sem áudio incorporado.
Áudio humano do Wikimedia Commons poderá ser baixado sob demanda em uma versão
posterior, com cache removível e metadados individuais de autoria e licença.

## Licença e procedência

Os metadados completos do primeiro pacote e as transformações declaradas estão
em `NOTICE-DATA.md`. A licença do código e a licença dos dados permanecem
separadas.

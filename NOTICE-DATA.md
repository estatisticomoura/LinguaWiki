# Aviso e atribuição dos dados

## Código e amostras internas

O código do aplicativo está sob a licença MIT (`LICENSE`). Essa licença não se
estende automaticamente aos pacotes de dicionário.

O vocabulário pequeno de `app/src/main/assets/seed_entries.json` foi escrito
para demonstrar o protótipo; não é uma extração do Wiktionary nem do FreeDict.

## Pacote `pt-pt`, versão `2026-09-02`

O pacote português–português é uma obra derivada de conteúdo do Wiktionary em
português:

- autoria: colaboradores do Wiktionary em português;
- projeto: https://pt.wiktionary.org/;
- extração estruturada: Wiktextract/Kaikki.org;
- arquivo-fonte: https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz;
- data declarada da extração: 2 de setembro de 2026;
- SHA-256 da fonte:
  `9c333f933157afa21234d4ffd4a6b8fe6eb61b279f6a5debd1d43b612faa8d16`;
- licença de redistribuição do pacote derivado: CC BY-SA 4.0;
- texto da licença: https://creativecommons.org/licenses/by-sa/4.0/.

Transformações realizadas pelo LinguaWiki:

1. seleção de registros cujo idioma do verbete é português;
2. normalização e reorganização em tabelas SQLite;
3. associação de registros exclusivamente flexionados aos respectivos lemas;
4. criação de chaves sem diacríticos e índices de lema, prefixo e forma;
5. preservação, quando disponível na extração, de definições, exemplos, IPA,
   etimologia, traduções e paradigmas flexionais;
6. compactação determinística do banco para distribuição.

Identidade do artefato publicado:

- `linguawiki-pt-pt-2026-09-02.sqlite.gz`;
- tamanho: 39.590.052 bytes;
- SHA-256:
  `cb8dd745870c1c55f13b8af0d34cdb84a2297da6a8b27ed2a447d449cbbfbcd8`;
- banco descompactado: 120.827.904 bytes;
- SHA-256 do banco:
  `bdb0550cbab742983dc5398f5f7f27d656b966d5fd30098ce2febbe704e40ac5`.

Ao redistribuir uma cópia ou adaptação do pacote, preserve esta atribuição,
indique novas modificações e cumpra os termos da CC BY-SA 4.0. Arquivos de
áudio não fazem parte deste pacote e podem ter licenças próprias.

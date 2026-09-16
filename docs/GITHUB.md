# GitHub: passo a passo do LinguaWiki

O repositório público é:
https://github.com/estatisticomoura/LinguaWiki

## 1. Entender as três áreas principais

- **Code**: código-fonte e documentação;
- **Actions**: compilação e testes automáticos a cada envio;
- **Releases**: arquivos grandes que as pessoas baixam, como APK e pacote de
  dicionário.

O banco de 39,6 MB não deve ser colocado como arquivo comum no histórico Git.
Ele fica como anexo de Release.

## 2. Conferir uma atualização

1. Abra o repositório.
2. Clique em **Actions**.
3. Abra a execução mais recente chamada **Android CI**.
4. Espere todos os passos ficarem verdes.
5. Se quiser o APK produzido automaticamente, abra a execução e baixe o
   artefato **LinguaWiki-debug-apk** no fim da página.

O artefato de Actions é temporário. Para oferecer uma versão pública estável,
use um Release.

## 3. Criar o Release `v0.4.0-prototype`

1. Na página do repositório, clique em **Releases**.
2. Clique em **Draft a new release**.
3. Em **Choose a tag**, escreva `v0.4.0-prototype`.
4. Selecione **Create new tag: v0.4.0-prototype on publish**.
5. Confirme que o alvo é a ramificação `main`.
6. Em **Release title**, escreva `LinguaWiki 0.4.0 prototype`.
7. Marque **Set as a pre-release**, porque ainda é uma versão de teste.
8. Arraste para a caixa de anexos estes quatro arquivos, sem alterar os nomes:

   - `LinguaWiki-prototype-0.4.0-debug.apk`
   - `linguawiki-pt-pt-2026-09-02.sqlite.gz`
   - `pt-pt-2026-09-02.json`
   - `NOTICE-DATA.md`

9. Espere cada barra de upload terminar.
10. Clique em **Publish release**.

Esse nome de tag é importante: o catálogo do APK aponta exatamente para o URL
gerado por esse Release.

## 4. Testar o download no celular

1. Abra o Release pelo navegador do celular.
2. Baixe e instale o APK.
3. Abra **Configurações → Dicionários offline** no app.
4. Confirme o download de português.
5. Se aparecer “arquivo inválido”, confira se o anexo mantém o nome exato e se
   foi enviado ao Release da tag correta.

## 5. Fazer uma alteração pelo site

Para editar somente um texto pequeno:

1. abra o arquivo na aba **Code**;
2. clique no lápis **Edit this file**;
3. faça a mudança;
4. clique em **Commit changes**;
5. escreva uma descrição curta;
6. escolha criar uma nova ramificação e Pull Request quando a alteração for de
   código; para uma correção documental simples, um commit direto em `main`
   também funciona.

Antes de mesclar mudanças de código, confirme que o **Android CI** ficou verde.

## 6. Versões futuras

Cada pacote deve usar uma URL imutável, com tag e versão dos dados. Ao atualizar
o Wiktionary:

1. gere um novo pacote e manifesto;
2. execute testes e verifique hashes;
3. atualize o catálogo do aplicativo;
4. aumente `versionCode` e `versionName`;
5. crie uma nova tag e um novo Release;
6. nunca substitua silenciosamente o arquivo de uma versão antiga.

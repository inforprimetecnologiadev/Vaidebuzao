# Como instalar o Vai de Buzão no seu Android

O app **não está na Play Store**. Ele é instalado direto pelo arquivo APK, de graça.

## 1. Baixar o APK

Abra no navegador do celular a página de versões do projeto:

**https://github.com/inforprimetecnologiadev/vaidebuzao/releases**

> Se o repositório for privado, entre na sua conta do GitHub no navegador antes.

- **Versão oficial:** a mais recente com nome `v1.x.x`. Baixe o arquivo `VaiDeBuzao-vX.Y.Z.apk`.
- **Versão de teste:** a pré-versão `teste`. Ela é gerada a cada mudança no código e aparece no celular
  como **"Vai de Buzão (teste)"**. Pode ficar instalada junto com a oficial.

## 2. Permitir a instalação

Na primeira vez, o Android avisa que o app vem de "fonte desconhecida":

1. Toque em **Configurações** no aviso;
2. Ative **Permitir desta fonte** para o navegador (Chrome, por exemplo);
3. Volte e toque em **Instalar**.

Se o Play Protect perguntar, toque em **Instalar mesmo assim**. O app é de código aberto e não tem anúncios
nem rastreadores.

## 3. Atualizar

Baixe o APK da versão nova e instale por cima: seus favoritos e preferências continuam lá.

> Se aparecer **"Aplicativo não instalado"** ao atualizar, a versão nova foi assinada com outra chave.
> Veja a seção de assinatura no `README.md`.

## Problemas comuns

| Problema | O que fazer |
|---|---|
| "Arquivo inválido" ou "pacote corrompido" | Baixe de novo, pelo Wi-Fi se possível |
| O mapa não aparece | Verifique a internet. O mapa precisa de conexão; pontos e rotas funcionam offline |
| Não acha minha localização | Ligue o GPS e permita a localização em Ajustes › Privacidade › Permissão de localização |
| Não mostra qual ônibus pegar | Ainda faltam linhas na base de dados. Veja `data/linhas/LEIA-ME.md` |

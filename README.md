# Vai de Buzão

<img src="branding/icone-512.png" width="140" alt="Logo Vai de Buzão" align="right">

App Android **gratuito** para quem anda de ônibus em **Cuiabá, Várzea Grande e região**:

- 📍 ponto de ônibus **mais perto de você**, com distância e tempo a pé;
- 🚌 **quais linhas passam** em cada ponto e em que sentido;
- 🧭 **qual linha pegar** para chegar ao destino: onde embarcar, onde descer e onde fazer baldeação.

Sem anúncios, sem cadastro, sem rastreadores. Seus dados ficam só no seu celular (LGPD).

## Baixar e instalar

👉 **[Releases](https://github.com/inforprimetecnologiadev/vaidebuzao/releases)**: versão oficial `vX.Y.Z`
ou a pré-versão **`teste`**, atualizada a cada mudança. Passo a passo: [docs/COMO-INSTALAR.md](docs/COMO-INSTALAR.md).

## ⚠️ Situação dos dados (importante)

O app usa dados abertos do **OpenStreetMap**. Hoje há cerca de **900 pontos de ônibus** e mais de **600
lugares** (bairros, hospitais, terminais...) mapeados na região, mas **nenhuma linha urbana** cadastrada.
Por isso o app já mostra os pontos próximos, mas **ainda não consegue dizer qual ônibus pegar**.

Três caminhos gratuitos para resolver:
1. **Pedir os dados oficiais** pela Lei de Acesso à Informação: modelo pronto em [docs/PEDIDO-LAI.md](docs/PEDIDO-LAI.md).
2. **Cadastrar linhas** conferidas em `data/linhas/*.json`: [guia](data/linhas/LEIA-ME.md).
3. **Mapear as linhas no OpenStreetMap**, que entram na atualização automática mensal.

Relatório atualizado da base: [docs/dados-relatorio.md](docs/dados-relatorio.md).

## Como o projeto funciona

| Parte | Onde |
|---|---|
| App Android (Kotlin, Jetpack Compose, Material 3) | `app/` |
| Núcleo testável: rotas, pontos próximos, regras de consentimento | `core/` |
| Pipeline de dados (OSM/GTFS/linhas cadastradas → `transit.db`) | `tools/data/` |
| Gerador de ícones a partir do logo | `tools/icon/` |
| Design system | [docs/design-system.md](docs/design-system.md) |
| Termos e Política de Privacidade | [docs/legal/](docs/legal/) |
| Regras para o Claude Code | [CLAUDE.md](CLAUDE.md) |
| Histórico de versões | [CHANGELOG.md](CHANGELOG.md) |

Tudo é compilado de graça no **GitHub Actions**:
- `build.yml`: a cada push roda testes e lint e publica o APK de teste.
- `release.yml`: ao criar uma tag `vX.Y.Z`, publica o APK oficial assinado.
- `atualizar-dados.yml`: atualiza pontos e linhas todo mês.

## Assinatura do APK (faça uma vez)

Para que as atualizações instalem por cima da versão anterior, o APK oficial precisa ser sempre assinado com
a **mesma chave**. Cadastre no GitHub, pelo navegador do celular, em **Settings › Secrets and variables ›
Actions › New repository secret**:

| Secret | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | O arquivo `.jks` em base64 |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | Apelido da chave |
| `KEY_PASSWORD` | Senha da chave |

Peça ao Claude Code: *"gere um keystore de release e me mostre como cadastrar os secrets"*. **Guarde uma
cópia do arquivo `.jks` e das senhas** (num Google Drive pessoal, por exemplo). Sem elas, versões futuras
não instalam por cima. Enquanto os secrets não existirem, o release sai assinado com a chave de debug.

## Avisos legais

- App independente: **não é oficial** e não tem ligação com prefeituras, Governo do Estado ou empresas de ônibus.
  Tempos são **estimativas**.
- Dados © colaboradores do OpenStreetMap (ODbL). Mapas: OpenFreeMap / OpenMapTiles.
- Fontes Inter e Montserrat: SIL Open Font License (`licenses/`).
- Termos e Política de Privacidade são **modelos elaborados com apoio de IA; recomenda-se revisão por advogado(a)**.

© InforPrime Tecnologia · Cuiabá – MT

# CLAUDE.md — regras do projeto Vai de Buzão

App Android (APK por sideload) de transporte por ônibus para Cuiabá, Várzea Grande e região.
Especificação completa e original: `docs/PROMPT_CLAUDE_CODE.md`. O dono desenvolve **pelo Claude Code no
celular**: não tem Android Studio nem computador. Explique tudo em português simples.

## Regras inegociáveis
- **Custo zero.** Proibido Google Maps SDK/Directions/Places, Firebase pago, APIs com chave/cartão ou servidor
  próprio. Mapa: MapLibre + OpenFreeMap. Busca: offline primeiro, depois Nominatim (máx. 1 req/s, User-Agent).
- **Nunca invente** linhas, números, itinerários ou horários. Sem dado → estado vazio honesto + "ajudar a melhorar".
- **Privacidade (LGPD):** sem analytics, rastreadores, anúncios ou backend. Localização só em memória e só com
  o app aberto. Dados do usuário só no aparelho (`allowBackup=false`). Não adicionar permissões sem necessidade.
- Não copiar marca, telas ou textos de outros apps (ex.: Moovit).
- Toda imagem do app é o logo `branding/logo-vai-de-buzao.png`. Ícones: rode `python3 tools/icon/gerar_icones.py`.

## Arquitetura
- `core/`: Kotlin puro (sem Android), testável em qualquer JVM: `geo`, `transit` (TransitNetwork, índice
  espacial), `routing` (Router estilo RAPTOR, sem horários), `consent` (regras e relógio de Cuiabá).
- `app/`: Compose + Material 3. DI manual em `data/AppContainer.kt` (sem Hilt/KSP, de propósito: menos risco
  de build sem Android Studio). Persistência: DataStore (preferências) + SQLiteOpenHelper (`usuario.db`:
  `consent_log` somente inserção, `favoritos`, `historico`). Base de transporte: `assets/transit.db` (somente
  leitura), carregada em memória.
- Telas em `ui/screens`, estado em `ui/state` (AppViewModel = portão LGPD/tema; TransitViewModel = mapa/rotas).
- Design system: `ui/theme` + `ui/components`, documentado em `docs/design-system.md`.

## Dados de ônibus
- Pipeline: `tools/data/gerar_transit_db.py` (só biblioteca padrão do Python). Fontes em ordem: GTFS
  (`data/gtfs/*.zip`) > OSM (Overpass) + linhas cadastradas (`data/linhas/*.json`, ver LEIA-ME).
- Roda no GitHub Actions (`atualizar-dados.yml`): em push em `tools/data/**`, `data/**` e todo mês. A rede do
  container do Claude Code bloqueia o Overpass, então não tente rodar o download aqui; use `--entrada` com um
  JSON local para testar.
- Testes: `python3 -m unittest discover -s tools/data/tests`.
- **Situação em nov/2026:** o OSM tem cerca de 900 pontos e nenhuma linha urbana da região. O valor do app
  depende de cadastrar linhas (data/linhas, GTFS via LAI, ou mapeamento no OSM). Veja `docs/PEDIDO-LAI.md`.

## Build e testes
- Aqui no container **não há Android SDK** (dl.google.com bloqueado). O módulo `core` dá para testar localmente
  com o Gradle de `/opt/gradle-8.14.3` num projeto temporário que inclui só o `:core`.
- Fonte da verdade: GitHub Actions. `build.yml` roda em todo push e publica a pré-versão **`teste`**
  (`.../releases/tag/teste`); `release.yml` roda na tag `vX.Y.Z` e publica o APK oficial assinado.
- Acompanhe os builds com as ferramentas MCP do GitHub (actions_list / get_job_logs) e corrija as falhas sozinho.
- Versões fixadas em `gradle/libs.versions.toml` (AGP 8.13, Kotlin 2.2.21, Compose BOM 2025.08.00). Não suba
  versões sem necessidade: o build só é verificado na nuvem.

## Versionamento e release
- `version.properties` → `VERSION_NAME` (SemVer). `versionCode = MAJOR*10000 + MINOR*100 + PATCH`.
- Debug usa o sufixo `.debug` (instala ao lado da versão oficial). `applicationId` nunca muda.
- Para lançar: atualize `VERSION_NAME` e o `CHANGELOG.md`, faça merge no `main` e crie a tag `vX.Y.Z`.
- Assinatura: secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Nunca commitar keystore.
- Commits no padrão Conventional Commits, em português.

## LGPD
- Textos: `app/src/main/assets/legal/{termos,privacidade}.md` + `config.json` (dados do controlador, versões).
  Mudou o texto → suba a versão em `config.json` e preencha `novidades`: o app pede novo aceite.
- Cópias legíveis em `docs/legal/` (gere de novo a partir dos assets ao alterar).
- Pendências: `[PREENCHER: CNPJ]` e o nome do(a) encarregado(a) em `config.json`. Recomenda-se revisão jurídica.

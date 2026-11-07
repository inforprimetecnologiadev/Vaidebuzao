# Prompt para o Claude Code — APK "Vai de Buzão"

> **Como usar (no Claude Code do celular):** abra uma sessão nova neste repositório
> (`inforprimetecnologiadev/vaidebuzao`) e envie apenas:
>
> `Leia o arquivo docs/PROMPT_CLAUDE_CODE.md e execute tudo o que ele pede, fase por fase.`
>
> Assim você não precisa colar este texto enorme no celular.

---

## 0. Seu papel

Você é, ao mesmo tempo:

- **Engenheiro(a) Android sênior** (Kotlin, Jetpack Compose, Material 3, arquitetura limpa, Gradle, CI/CD);
- **Designer sênior de UI/UX** (pesquisa de usuário, arquitetura de informação, acessibilidade, microinterações);
- **Designer gráfico sênior** (identidade visual, tipografia, cor, iconografia, consistência de marca);
- **Especialista em privacidade e proteção de dados no Brasil** (LGPD e legislação correlata).

Trabalhe com qualidade de produto profissional, mas explique tudo para mim em **português simples**, porque
estou desenvolvendo **pelo Claude Code no celular**: não tenho Android Studio, não tenho computador e não vou
rodar comandos na mão. Todo build tem que acontecer **na nuvem (GitHub Actions)** ou no seu próprio container.

## 1. O que é o app

**Nome:** Vai de Buzão
**Plataforma:** somente Android, entregue como **APK instalável diretamente** (sideload, sem Play Store).
**Região atendida:** Cuiabá, Várzea Grande e região metropolitana (Vale do Rio Cuiabá — inclua no recorte
geográfico também Nossa Senhora do Livramento, Santo Antônio de Leverger, Chapada dos Guimarães e Acorizal,
quando houver dados).
**Inspiração de funcionalidade:** apps de mobilidade como o Moovit — **apenas como referência de funcionalidade**.
Não copie nome, marca, telas, cores, textos nem ícones de nenhum app existente; a identidade é 100% do Vai de Buzão.

### Problema que o app resolve

Sou novo(a) em Cuiabá e preciso andar de ônibus. O app deve me dizer:

1. **Qual é o ponto de ônibus mais próximo** da minha localização atual (com distância e tempo a pé);
2. **Quais linhas passam nesse ponto** (número, nome, sentido/destino);
3. **Para ir até o lugar que eu quero**, em qual ponto embarcar, **qual linha pegar**, onde descer,
   se há baldeação e quanto vou caminhar.

## 2. Restrição principal: CUSTO ZERO

Não quero gastar nenhum centavo. Portanto:

- **Proibido**: Google Maps SDK/Directions/Places (exigem cartão de crédito/billing), Firebase pago, qualquer API
  paga ou que exija cartão, servidor/backend pago, conta paga de desenvolvedor.
- **Use apenas**: bibliotecas open source, dados abertos e serviços gratuitos sem chave e sem cartão.
- **Distribuição**: APK em **GitHub Releases** deste repositório (não precisa de Play Store, que cobra taxa).
- **Build**: GitHub Actions (gratuito dentro da cota mensal da conta). Mantenha os workflows enxutos
  (cache do Gradle, sem matrizes desnecessárias) para não estourar a cota.
- Se algo só puder ser feito pagando, **pare e me avise** com a alternativa gratuita.

## 3. Stack técnica obrigatória

- **Kotlin** + **Jetpack Compose** + **Material 3**; versões estáveis mais recentes e compatíveis entre si,
  fixadas em `gradle/libs.versions.toml` (version catalog). JDK 17. Gradle Wrapper commitado.
- `minSdk 24`, `targetSdk`/`compileSdk` = o mais recente estável.
- **applicationId:** `br.com.inforprimetecnologia.vaidebuzao` (nunca mude depois do primeiro release).
- Arquitetura: MVVM + camadas `data` / `domain` / `ui`, injeção com **Hilt**, **Coroutines/Flow**,
  **Navigation Compose**, **Room** (banco local), **DataStore** (preferências), **WorkManager** se necessário.
- **Mapa:** **MapLibre Android SDK** com tiles gratuitos e sem chave (ex.: OpenFreeMap), com estilo claro e
  estilo escuro. Alternativa aceitável: osmdroid. Exiba a atribuição obrigatória
  **"© colaboradores do OpenStreetMap"** no mapa e na tela Sobre (licença ODbL).
- **Localização:** `FusedLocationProviderClient` com fallback para `LocationManager` (para aparelhos sem Google Play Services).
- **Busca de endereço (geocodificação):** primeiro busca **local/offline** (pontos, bairros, terminais e locais
  conhecidos já embutidos no app); depois, como fallback, **Nominatim** ou **Photon** (gratuitos), respeitando a
  política de uso (máx. 1 req/s, User-Agent identificando o app, cache, debounce) e limitando a busca ao
  recorte da região. Isso deve constar na Política de Privacidade (o texto digitado vai para um terceiro).
- **Rota a pé até o ponto:** estimativa local (distância geodésica × fator de correção, 4,5 km/h) e botão
  "Abrir no app de mapas" via `Intent` `geo:`/navegação (gratuito).
- **Qualidade:** ktlint ou detekt, Android Lint, testes unitários (JUnit) para roteamento, consentimento e
  repositórios; R8/minify ligado no release; APK final idealmente < 30 MB.
- **Sem analytics, sem rastreadores, sem anúncios.** Isso simplifica a LGPD e é um diferencial do app.

## 4. Dados de ônibus (a parte mais importante — seja honesto comigo)

Não existe garantia de que haja dados oficiais abertos das linhas de Cuiabá/VG. Faça assim:

1. **Pesquise** (se tiver acesso à web) se existe feed **GTFS** público para Cuiabá, Várzea Grande ou
   intermunicipal (Mobility Database, Transitland, portais das prefeituras/órgão estadual). Me diga o que achou.
2. Crie um **pipeline de dados** em `tools/data/` (script Python ou Kotlin) que:
   - Se houver um GTFS em `data/gtfs/*.zip`, usa ele como fonte **prioritária**;
   - Caso contrário (ou para complementar), baixa do **OpenStreetMap via Overpass API** os pontos
     (`highway=bus_stop`, `public_transport=platform`/`stop_position`), terminais e as relações de linha
     (`type=route` + `route=bus`, e `route_master`) dentro do recorte geográfico da região;
   - Normaliza tudo num banco **SQLite** pré-pronto (`app/src/main/assets/transit.db`) com tabelas de
     pontos, linhas, sentidos, sequência de pontos por sentido, geometria do trajeto e metadados
     (fonte, data de geração, versão dos dados);
   - Gera um **relatório de qualidade** (`docs/dados-relatorio.md`): quantos pontos, quantas linhas,
     linhas sem pontos, pontos órfãos, etc.
3. Crie o workflow **`.github/workflows/atualizar-dados.yml`** (manual `workflow_dispatch` + mensal) que roda o
   pipeline na nuvem e commita o banco atualizado — assim não dependo do seu container ter acesso à internet.
4. **Roteamento offline no próprio celular** (sem servidor): implemente um algoritmo do tipo **RAPTOR** (ou
   busca em grafo equivalente) sobre o banco local:
   - Candidatos: pontos num raio caminhável da origem e do destino (padrão 800 m, configurável);
   - Opções: linha direta, 1 baldeação e no máximo 2 baldeações;
   - Ordenação: tempo total estimado, depois menos baldeações, depois menos caminhada;
   - Sem horários oficiais, use velocidade média de ônibus e frequência estimada, e **deixe claro na tela**
     que os tempos são estimativas.
5. **Nunca invente linhas, números, itinerários ou horários.** Se não houver dado, a interface mostra um estado
   vazio bem desenhado ("Ainda não temos dados desta linha/região") e um botão **"Reportar/ajudar a melhorar"**
   (abre e-mail ou issue do GitHub, sem coletar dados extras).
6. Ônibus em tempo real (GPS dos veículos) **não** está no escopo, pois não há fonte gratuita confiável.
   Deixe a arquitetura preparada para isso no futuro.

## 5. Funcionalidades e telas

Navegação inferior: **Início (mapa)** · **Linhas** · **Favoritos** · **Ajustes**.

1. **Splash** (API SplashScreen do Android) com o logo.
2. **Primeiro acesso — Termos e Privacidade** (ver seção 7). O app não abre sem essa etapa.
3. **Onboarding** curto (3 telas) explicando o app, e **tela de pré-permissão** explicando por que a localização
   é pedida *antes* do diálogo do sistema. Se o usuário negar, o app continua funcionando com escolha manual do
   local no mapa ou por busca.
4. **Início / Mapa:** minha posição, pontos próximos destacados, *bottom sheet* com a lista dos pontos mais
   próximos (distância, minutos a pé, linhas que passam), campo grande **"Para onde vamos?"**.
5. **Detalhe do ponto:** nome/referência, linhas que passam (número, nome, sentido), favoritar, "Como chegar a pé".
6. **Busca de destino → Opções de rota:** cartões comparáveis (tempo total, linhas, baldeações, caminhada).
7. **Detalhe do itinerário:** passo a passo (caminhe até o ponto X → pegue a linha Y sentido Z → desça no
   ponto W após N pontos → caminhe até o destino), com trajeto desenhado no mapa.
8. **Linhas:** busca por número ou nome, detalhe da linha com itinerário no mapa e lista de pontos por sentido.
9. **Favoritos e histórico:** pontos, linhas e destinos (casa, trabalho, faculdade), tudo salvo **só no aparelho**.
10. **Ajustes:** Tema (**Claro / Escuro / Automático-sistema**), Privacidade (ver seção 7), Termos de Uso,
    Política de Privacidade, Sobre (versão do app, versão/data dos dados, atribuições OSM, licenças open source),
    Reportar problema.
11. *(Fase opcional)* **Aviso de descida:** notificação quando estiver chegando ao ponto de descida, usando
    serviço em primeiro plano só durante a viagem, com consentimento específico.

Permissões permitidas: `INTERNET`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`
(+ `POST_NOTIFICATIONS` e `FOREGROUND_SERVICE_LOCATION` somente se a fase opcional for feita).
**Nada de localização em segundo plano, contatos, câmera, armazenamento ou outras.**

## 6. Design System (nível sênior)

Crie um design system completo, documentado em `docs/design-system.md` e implementado em
`app/src/main/java/.../ui/theme/` + `ui/components/`.

- **Identidade:** extraída do logo (`branding/logo-vai-de-buzao.png`). Paleta de partida amostrada da imagem
  (refine com conta-gotas real e valide contraste):
  - Azul-marinho profundo ≈ `#0A1A5C` · Azul royal ≈ `#1446C8` · Azul céu ≈ `#3FA9F5`
  - Amarelo-ouro ≈ `#FFC800` · Laranja-pino ≈ `#FF8A00` · Branco `#FFFFFF`
  - Personalidade: alegre, popular, confiável, "cuiabano", direto ao ponto.
- **Tokens:** cores (esquemas `light` e `dark` completos do Material 3, com papéis semânticos: sucesso,
  alerta, erro, informação, "linha de ônibus", "caminhada", "ponto"), tipografia, espaçamentos (grade de 4/8 dp),
  raios de canto, elevação, opacidades, durações e curvas de animação.
- **Tipografia:** fontes gratuitas (licença OFL) **embutidas no APK**, sem download em tempo de execução — ex.:
  uma sans-serif pesada/condensada para títulos (combinando com o lettering do logo, como *Montserrat* ou
  *Poppins* Black/ExtraBold) e *Inter* ou similar para textos.
- **Temas:** claro, escuro e automático (segue o sistema), troca instantânea sem reiniciar, preferência salva no
  DataStore, mapa também muda para o estilo escuro, barras de status/navegação coerentes (edge-to-edge).
  Não use cor dinâmica do Android (Material You) por padrão, para preservar a marca.
- **Componentes:** botões, campo de busca, chip de linha (número da linha em "placa" colorida), cartão de ponto,
  cartão de rota, linha do tempo do itinerário, bottom sheet, estados vazio/erro/carregando (skeleton),
  diálogos, snackbars, tela de consentimento.
- **Acessibilidade (obrigatório):** contraste mínimo WCAG AA, alvos de toque ≥ 48 dp, suporte a fonte grande
  (até 200%) sem quebrar layout, descrições para TalkBack, não depender só de cor para informar algo.
  (Lei Brasileira de Inclusão — Lei 13.146/2015.)
- **Microinterações:** transições suaves, feedback tátil leve, animação do pino/ônibus no carregamento —
  sem exagero e respeitando "remover animações" do sistema.
- **Catálogo:** uma tela interna "Design System" (só no build debug) mostrando todos os componentes nos dois temas.
- **Imagens do app:** **toda imagem/ilustração do app deve usar o logo `branding/logo-vai-de-buzao.png`**
  (splash, onboarding, estados vazios, tela Sobre, cabeçalho dos termos, ícone). Não use outras ilustrações.

### Ícone do app

- Fonte única: `branding/logo-vai-de-buzao.png` (1254×1254, RGB **sem transparência**; os cantos fora do
  quadrado arredondado são **pretos** e devem ser removidos/mascarados).
- Crie um script `tools/icon/gerar_icones.py` (Pillow) que gere: ícones legados (`mipmap-mdpi` … `xxxhdpi`,
  normal e round), **ícone adaptativo** (`foreground` com a arte dentro da área segura + `background` em
  azul da própria arte, para não cortar o texto "VAI DE BUZÃO"), ícone 512×512 e a imagem da splash.
- Gere **pré-visualizações** com máscaras círculo, squircle e quadrado arredondado, **olhe para elas**, ajuste
  até nada importante ficar cortado e commite as prévias em `branding/previews/`.

## 7. LGPD, leis brasileiras, termos e consentimento

### Legislação a considerar (e citar nos textos)

- **LGPD — Lei 13.709/2018**: princípios (art. 6º), bases legais (art. 7º), consentimento livre, informado,
  inequívoco e **revogável** (art. 8º, §5º), informação clara (art. 9º), crianças e adolescentes (art. 14),
  direitos do titular (art. 18), encarregado (art. 41), segurança (art. 46), incidentes (art. 48), ANPD.
- **Marco Civil da Internet — Lei 12.965/2014** (art. 7º) e **Decreto 8.771/2016**.
- **Código de Defesa do Consumidor — Lei 8.078/1990** (cláusulas claras e destacadas, arts. 46, 47 e 54).
- **Lei Brasileira de Inclusão — Lei 13.146/2015** (acessibilidade).
- Foro: **Cuiabá – MT**. Fuso horário: **America/Cuiaba (UTC−4)**.

### Princípio de design de privacidade

**Privacidade desde a concepção:** a localização é processada **somente no aparelho**; o app não tem servidor
próprio e não envia dados pessoais para nós. Os únicos envios a terceiros são: tiles do mapa (servidor de mapas)
e o texto da busca de endereço (Nominatim/Photon) — declare isso de forma clara.

### Textos legais

- Escreva em português claro, com sumário e linguagem acessível (com uma versão "resumo em 30 segundos" no topo):
  - `Termos de Uso` (objeto, gratuidade, limitações — dados e tempos são **estimativas**, o app não é oficial
    nem ligado às empresas de ônibus ou prefeituras, responsabilidades, propriedade intelectual, atribuição OSM,
    alterações dos termos, foro de Cuiabá-MT);
  - `Política de Privacidade` (controlador, encarregado/DPO e contato, quais dados, finalidade, base legal,
    compartilhamento com terceiros, retenção, segurança, direitos do art. 18 e como exercê-los no próprio app,
    menores de idade, contato com a ANPD, histórico de versões).
- Versione os textos (ex.: `termos-v1.0.0`, `privacidade-v1.0.0`) em `app/src/main/assets/legal/` (Markdown
  renderizado no app) e publique cópia em `docs/legal/` no repositório.
- Dados do controlador ficam em um único arquivo de configuração (ex.: `legal/config.json`).
  **Antes de escrever os textos, me pergunte**: nome/razão social, CPF ou CNPJ, e-mail de contato do
  encarregado e cidade. Se eu não souber, use marcadores bem visíveis como `[PREENCHER: CNPJ]` e me lembre deles
  no final.
- Adicione no README o aviso: *"Modelos elaborados com apoio de IA; recomenda-se revisão por advogado(a)."*

### Fluxo de aceite ou recusa (primeiro acesso e a cada nova versão dos termos)

1. Tela com resumo + links para ler os textos completos (abertos dentro do app).
2. Caixas de seleção **separadas** e **desmarcadas por padrão**:
   - [obrigatória] "Li e aceito os Termos de Uso";
   - [obrigatória] "Li e estou ciente da Política de Privacidade";
   - [opcional] "Permitir salvar histórico de buscas no aparelho";
   - [opcional, se a fase existir] "Permitir aviso de descida".
3. Confirmação de idade: "Tenho 18 anos ou mais, ou uso com autorização do responsável" (art. 14).
4. Botões **"Aceitar e continuar"** (habilitado só com as obrigatórias marcadas) e **"Recusar"**.
5. **Recusar** → tela explicando que o app não pode ser usado sem aceitar, com "Rever termos" e "Sair do app".
   A recusa também é registrada.
6. Quando a versão dos termos/política mudar, mostrar o que mudou e pedir novo aceite.

### Registro do consentimento (data e hora do aceite)

Tabela Room **somente de inserção** (histórico imutável) `consent_log` com:
`id (UUID)`, `acao (ACEITO | RECUSADO | REVOGADO | ATUALIZADO)`, `versao_termos`, `versao_privacidade`,
`hash_sha256_termos`, `hash_sha256_privacidade`, `consentimentos_opcionais (JSON)`,
`data_hora_local` (ISO-8601 com offset, ex. `2026-11-07T15:42:10-04:00`, fuso America/Cuiaba),
`data_hora_utc`, `versao_app`, `versao_dados`.
Seja transparente: como não há servidor, o registro fica **no aparelho** (explique isso na política).

### Central de Privacidade (em Ajustes)

- **Meus consentimentos:** histórico com data e hora de cada aceite/recusa/revogação.
- **Gerenciar permissões:** ligar/desligar opcionais e atalho para as permissões do sistema.
- **Revogar consentimento:** volta para a tela de termos e registra `REVOGADO`.
- **Exportar meus dados (portabilidade/acesso):** gera um JSON com favoritos, histórico, preferências e o log de
  consentimento, compartilhado via *share sheet* do Android.
- **Apagar meus dados:** apaga tudo localmente (com confirmação), mantendo apenas o registro mínimo da exclusão.
- Contato do encarregado, link para a ANPD.
- Configurar `android:allowBackup`/`dataExtractionRules` de forma coerente com a política.

## 8. Ícone, versionamento, assinatura e entrega do APK

- **Versionamento semântico** (`MAJOR.MINOR.PATCH`), `versionCode` crescente automático (derivado da tag ou do
  número do run), `CHANGELOG.md` no padrão *Keep a Changelog*, commits no padrão *Conventional Commits*.
- **Workflows do GitHub Actions:**
  - `build.yml` — em todo push e PR: lint + testes + **APK debug** como artefato;
  - `release.yml` — ao criar tag `v*` (e também manual via `workflow_dispatch` informando a versão): gera
    **APK release assinado** e cria um **GitHub Release** com o APK anexado
    (nome `VaiDeBuzao-vX.Y.Z.apk`) e as notas do CHANGELOG;
  - `atualizar-dados.yml` — ver seção 4.
- **Assinatura:** o workflow lê o keystore de *GitHub Secrets* (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`). Se os secrets não existirem, gera o APK assinado com chave de debug e avisa no
  Release. **Nunca commite keystore ou senhas.** Me explique, passo a passo e pelo celular (site do GitHub →
  Settings → Secrets and variables → Actions), como cadastrar os secrets, e me alerte para **guardar uma cópia
  do keystore**: sem ela, futuras versões não instalam por cima da anterior.
- Como acompanhar o build: use as ferramentas do GitHub (MCP) para ver o status e os logs dos workflows e
  corrigir falhas sozinho, sem me pedir para abrir logs.
- Tente também compilar no seu container; se o download do Android SDK for bloqueado pela rede, use o
  GitHub Actions como fonte da verdade.
- `docs/COMO-INSTALAR.md`: passo a passo, com linguagem simples, para baixar o APK pelo celular na página de
  Releases, permitir "instalar apps desconhecidos" e atualizar para versões novas.

## 9. Organização do repositório

```
.github/workflows/      build.yml, release.yml, atualizar-dados.yml
app/                    código Android
branding/               logo-vai-de-buzao.png (fonte única), previews/
data/gtfs/              (opcional) feeds GTFS oficiais, se existirem
docs/                   design-system.md, legal/, dados-relatorio.md, COMO-INSTALAR.md, este prompt
tools/data/             pipeline OSM/GTFS -> transit.db
tools/icon/             gerador de ícones
CLAUDE.md               regras do projeto para as próximas sessões
CHANGELOG.md, README.md
```

Crie um `CLAUDE.md` com as decisões deste documento (custo zero, stack, regras de privacidade, design system,
fluxo de release) para que as próximas sessões sigam o mesmo padrão.

## 10. Plano de execução (faça por fases, com commit e push ao fim de cada uma)

1. **Fase 0 – Planejamento:** me faça as perguntas que faltarem (dados do controlador para a LGPD) e mostre um
   plano curto. Não espere respostas para coisas que você pode decidir com bom senso.
2. **Fase 1 – Fundação:** projeto Android, version catalog, Hilt, navegação, `build.yml` funcionando no
   GitHub Actions (primeiro APK debug baixável), `CLAUDE.md`, README, CHANGELOG.
3. **Fase 2 – Design System + ícone:** tokens, temas claro/escuro/automático, fontes, componentes, catálogo,
   ícones gerados a partir do logo, splash.
4. **Fase 3 – Termos, privacidade e consentimento:** textos legais, fluxo de aceite/recusa, `consent_log`,
   Central de Privacidade.
5. **Fase 4 – Dados:** pipeline OSM/GTFS, `atualizar-dados.yml`, `transit.db`, relatório de qualidade.
6. **Fase 5 – Mapa e pontos próximos:** localização, pré-permissão, mapa claro/escuro, ponto mais próximo,
   detalhe do ponto e linhas que passam.
7. **Fase 6 – Roteamento:** busca de destino, algoritmo, opções de rota, itinerário passo a passo, testes.
8. **Fase 7 – Linhas, favoritos, histórico e ajustes.**
9. **Fase 8 – Polimento:** acessibilidade, estados vazios/erro, desempenho, tamanho do APK, revisão visual
   nos dois temas.
10. **Fase 9 – Release `v1.0.0`:** `release.yml`, assinatura, GitHub Release com o APK, `COMO-INSTALAR.md`.
11. *(Opcional)* **Fase 10 – Aviso de descida.**

Em cada fase: rode lint e testes, verifique o build na nuvem, gere capturas das telas nos temas claro e escuro
com testes de screenshot do Compose (Roborazzi ou Paparazzi, rodando na JVM, sem emulador), olhe para elas e
corrija o visual, e me mande um resumo curto do que mudou e **onde baixar o APK para testar**.

## 11. Regras gerais

- Nunca invente dados de linhas/horários; nunca use serviço pago; nunca colete dado pessoal sem necessidade.
- Não copie marca, textos ou telas de outros apps.
- Textos da interface em português do Brasil, tom amigável e direto (pode ter um toque regional, sem exagero).
- Commits pequenos e descritivos; push no branch de desenvolvimento indicado na sessão.
- Ao terminar, me entregue uma lista do que ficou pronto, o que ficou pendente e os `[PREENCHER: ...]` que faltam.

# Design System — Vai de Buzão

Implementação: `app/src/main/java/br/com/inforprimetecnologia/vaidebuzao/ui/theme/` (tokens) e
`ui/components/` (componentes). Catálogo vivo: **Ajustes › Design System**, só na versão de teste.

## 1. Essência da marca

**Alegre, popular, confiável e cuiabano.** Fala como gente da cidade: direto ao ponto, sem jargão.
O app resolve uma ansiedade real ("qual buzão eu pego?"), então a interface precisa ser **calma, legível sob
sol forte e rápida de entender com uma mão só**, em pé no ponto.

- **Logo:** `branding/logo-vai-de-buzao.png` é a **única** imagem ou ilustração do app (ícone, splash,
  onboarding, estados vazios, Sobre, cabeçalhos). Não use outras ilustrações.
- **Tom de voz:** "Para onde vamos?", "Pontos perto de você", "Pegue a linha 101", "Desça em…".
  Use "buzão" com moderação, como assinatura da marca.

## 2. Cor

Paleta amostrada do logo:

| Token | Hex | Uso |
|---|---|---|
| `Brand.Navy900` | `#06166A` | Fundo da splash, base de degradês |
| `Brand.Royal600` | `#1446C8` | **primary** (claro): ações, links, linha padrão |
| `Brand.Royal500` | `#1E5BF0` | Localização do usuário, topo do degradê |
| `Brand.Sky400` | `#3FA9F5` | Localização do usuário no escuro |
| `Brand.Yellow400` | `#FFC800` | **secondary**: botão de destaque, indicadores, estrelas |
| `Brand.Orange500` | `#FF8A00` | **Pontos de ônibus** no mapa e na lista (o "pino" do logo) |
| `Brand.Orange700` | `#C25600` | **tertiary** (claro): marcador manual, alertas suaves |

Papéis semânticos extras (`VdbTheme.colors`): `success`, `warning`, `info`, `walk` (caminhada), `stop`
(ponto), `userLocation`, `lineDefault`, `heroGradientTop/Bottom`.

**Contraste (WCAG AA):** texto branco sobre `#1446C8` dá cerca de 6,9:1. Texto `#1F1600` sobre `#FFC800`
dá cerca de 12:1. Texto principal `#0E1530` sobre `#F6F8FD` dá cerca de 16:1. No escuro, `#E3E7F5` sobre
`#0B1230` dá cerca de 14:1. **Nunca** use texto branco sobre amarelo.

**Cor dinâmica (Material You) desligada** de propósito, para preservar a marca.

## 3. Tipografia

Fontes livres (SIL OFL), **embutidas no APK** (`res/font/`), sem download em tempo de execução.

| Estilo | Fonte | Tamanho/linha | Uso |
|---|---|---|---|
| display* | Montserrat Black/ExtraBold | 52/42/34 sp | Raro: números grandes |
| headline* | Montserrat ExtraBold | 30/26/22 sp | Títulos de tela e tempo total da rota |
| titleLarge | Montserrat Bold | 20 sp | Barras de topo, seções fortes |
| title M/S | Inter SemiBold | 16/14 sp | Títulos de item de lista |
| body L/M/S | Inter Regular | 16/14/12 sp | Textos |
| label* | Inter SemiBold/Medium | 14/12/11 sp | Botões, pílulas, legendas |

O número da linha (`LineBadge`) usa Montserrat ExtraBold, como o letreiro do ônibus.

## 4. Espaço, forma, elevação e movimento

- **Grade de 4 dp:** `xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 · xxxl 48`. Margem de tela: 20 dp.
- **Cantos:** extraSmall 6 · small 10 · medium 16 (cartões, botões) · large 24 (busca, sheets) · extraLarge 32.
- **Elevação:** quase plana. Cartões usam borda `outlineVariant` no claro e superfície tonal no escuro.
  Só a busca flutuante sobre o mapa tem sombra (6 dp).
- **Movimento:** 150/250/400 ms; troca de telas por *fade*. As animações do Compose respeitam a opção
  "remover animações" do sistema.

## 5. Temas

**Claro, Escuro e Automático** (segue o sistema), em Ajustes › Aparência. A troca é instantânea, sem
reiniciar, e fica salva no DataStore. O mapa muda junto (OpenFreeMap *liberty* ↔ *dark*; se o escuro falhar,
volta para o claro). As barras de status e navegação acompanham o tema (edge-to-edge).

## 6. Componentes

| Componente | Onde | Notas |
|---|---|---|
| `VdbPrimaryButton` | Ações principais | Altura mínima de 52 dp |
| `VdbAccentButton` | Ação de marca (amarelo) | "Ir daqui", "Continuar" no onboarding |
| `VdbSecondaryButton` | Ações secundárias | Contorno |
| `VdbTopBar` | Topo das telas | Botão voltar com descrição para o TalkBack |
| `LineBadge` / `LineBadgeRow` | Número da linha | Cor da linha (OSM/GTFS) e texto com contraste automático |
| `StopRow` | Listas de pontos | Uma só descrição de acessibilidade por item |
| `PatternRow` | Listas de linhas/sentidos | |
| `RouteOptionCard` | Opções de rota | Tempo, baldeações, sequência de trechos, caminhada e espera |
| `TimelineStep` | Itinerário passo a passo | Linha contínua para ônibus, tracejada para caminhada |
| `InfoBanner` / `InfoPill` | Avisos e metadados | Variante de alerta |
| `EmptyState` | Vazio ou erro | Sempre com o logo e uma ação |
| `SkeletonList` | Carregando | Brilho suave |
| `VdbMap` | Mapa | MapLibre; camadas: pontos, trajeto, caminhada, marcadores, usuário |

## 7. Acessibilidade (obrigatório)

- Alvos de toque ≥ 48 dp (linhas de lista ≥ 56 dp).
- Funciona com fonte em 200%: listas sem altura fixa, textos com `maxLines` e reticências só onde é seguro.
- TalkBack: títulos marcados como *heading*; itens complexos (ponto, rota) com descrição única e completa.
- Informação nunca só por cor: a linha tem número, a caminhada tem ícone e texto, os alertas têm ícone.
- Lei Brasileira de Inclusão (Lei nº 13.146/2015).

## 8. Ícone

Gerado por `tools/icon/gerar_icones.py` a partir do logo: ícone adaptativo (logo de 62 dp dentro da zona
segura e fundo em degradê azul), ícones legados, versão redonda, splash do Android 12+ e o ícone de 512 px.
Prévias com as máscaras de cada launcher em `branding/previews/`.

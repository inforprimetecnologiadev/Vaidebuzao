# Changelog

Todas as mudanças relevantes do Vai de Buzão. Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

## [1.0.0] - 2026-11-07

### Adicionado
- **Gravar linha:** dentro do ônibus, o app grava o trajeto e você marca as paradas. A linha passa a valer
  na hora no aparelho (pontos, linhas e rotas) e pode ser enviada ao projeto pelo GitHub ou compartilhada.
  Rascunho automático, "gravar a volta" e "Minhas gravações".
- Política de Privacidade 1.1.0 (gravação de trajetos opcional, com consentimento registrado).
- Mapa (MapLibre + OpenFreeMap, gratuito) com os pontos de ônibus de Cuiabá, Várzea Grande e região.
- Ponto de ônibus mais próximo com distância e tempo a pé; escolha manual do local com toque longo no mapa.
- Detalhe do ponto com as linhas que passam e o sentido de cada uma.
- Busca de destino offline (bairros, terminais, hospitais, faculdades, shoppings) e online (Nominatim).
- Cálculo de rotas no próprio celular, com até 2 baldeações, e itinerário passo a passo.
- Lista de linhas com mapa do trajeto e pontos por sentido.
- Favoritos e histórico de buscas (opcional), somente no aparelho.
- Temas claro, escuro e automático; design system próprio com as cores do logo.
- LGPD: Termos de Uso e Política de Privacidade versionados; aceite ou recusa com registro de data e hora
  (fuso de Cuiabá e UTC); Central de Privacidade com exportação, revogação e exclusão de dados.
- Pipeline de dados (OpenStreetMap, GTFS e linhas cadastradas em `data/linhas/`) com atualização mensal.
- Builds e releases automáticos no GitHub Actions.

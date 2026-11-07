# Cadastro de linhas de ônibus (data/linhas)

Hoje o OpenStreetMap tem os **pontos** de ônibus de Cuiabá e Várzea Grande, mas **quase nenhuma linha**
cadastrada. Sem linhas, o app mostra o ponto mais próximo, mas não consegue dizer qual ônibus pegar.

Esta pasta permite cadastrar linhas **sem depender do OpenStreetMap**. Cada arquivo `*.json` aqui é lido pelo
pipeline (`tools/data/gerar_transit_db.py`), que roda sozinho no GitHub Actions a cada mudança nesta pasta e
gera um novo `transit.db` para o app.

> **Regra de ouro:** só cadastre linhas com informação **verdadeira e conferida** (itinerário oficial
> publicado pela prefeitura/operadora, ou trajeto que você mesmo conferiu). Nunca invente.

## Jeito mais fácil: gravar pelo app

Na aba **Linhas**, toque em **"Gravar linha"**. Dentro do ônibus, toque em **"Parou num ponto"** em cada
parada. No fim, use **"Enviar para o projeto (GitHub)"**: o GitHub abre com o arquivo já pronto nesta pasta, e
é só tocar em **"Commit changes"**. Ou use **"Compartilhar arquivo"** e mande o arquivo para o Claude Code.

O arquivo gerado pelo app usa `trajeto_polyline` (o trajeto compactado) no lugar de `trajeto`.

## Formato

Um arquivo pode ter uma ou várias linhas (lista). Exemplo: `data/linhas/cuiaba-101.json`

```json
[
  {
    "numero": "101",
    "nome": "101 – Nome oficial da linha",
    "operadora": "Nome da empresa (opcional)",
    "cor": "#1446C8",
    "intervalo_min": 20,
    "sentidos": [
      {
        "de": "Bairro de origem",
        "para": "Bairro de destino",
        "pontos": [
          { "lat": -15.60000, "lon": -56.10000, "nome": "Nome do ponto (opcional)" },
          { "lat": -15.60500, "lon": -56.09500 }
        ],
        "trajeto": [[-15.60000, -56.10000], [-15.60200, -56.09800], [-15.60500, -56.09500]]
      }
    ]
  }
]
```

- `pontos`: em **ordem de passagem**. Cada coordenada é ligada automaticamente ao ponto do OpenStreetMap
  mais próximo (até 35 m). Se não houver ponto perto, um novo é criado.
- `trajeto` (opcional): o desenho da rua por onde o ônibus passa. Sem ele, o mapa liga os pontos em linha reta.
- `intervalo_min` (opcional): intervalo médio entre ônibus. Ajuda a estimar a espera.
- Cadastre **cada sentido** (ida e volta) separadamente.

Dica para pegar coordenadas pelo celular: no Google Maps ou no OsmAnd, toque e segure no ponto e copie
a latitude e a longitude.

## Outras formas de conseguir os dados

1. **GTFS oficial:** se a prefeitura ou a operadora fornecerem um arquivo GTFS (`.zip`), coloque-o em
   `data/gtfs/`. Ele tem prioridade sobre tudo. Veja `docs/PEDIDO-LAI.md` para pedir de graça.
2. **OpenStreetMap:** linhas mapeadas no OSM (relações `type=route` + `route=bus`) entram automaticamente
   na atualização mensal. Guia: https://wiki.openstreetmap.org/wiki/Pt:Public_transport

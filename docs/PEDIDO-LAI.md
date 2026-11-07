# Como pedir os dados oficiais das linhas (de graça)

A **Lei de Acesso à Informação (Lei nº 12.527/2011)** garante que qualquer pessoa pode pedir informações
públicas a órgãos do governo, **sem custo e sem precisar justificar**. O prazo de resposta é de **20 dias**,
prorrogável por mais 10.

Linhas, itinerários, pontos e horários do transporte coletivo são informações públicas. Peça nos canais
oficiais de cada órgão: portal de transparência, e-SIC ou Ouvidoria.

| Quem | O que cuida | Onde pedir |
|---|---|---|
| Prefeitura de Cuiabá (órgão municipal de mobilidade) | Linhas urbanas de Cuiabá | Portal da Transparência / e-SIC da Prefeitura de Cuiabá |
| Prefeitura de Várzea Grande | Linhas urbanas de Várzea Grande | Portal da Transparência / e-SIC da Prefeitura de VG |
| Governo de MT (órgão regulador do transporte intermunicipal) | Linhas entre Cuiabá, VG e região | Ouvidoria / Fale Conosco do Governo de MT |

## Modelo de pedido (copie e cole)

> Prezados,
>
> Com base na Lei nº 12.527/2011 (Lei de Acesso à Informação), solicito, em **formato aberto e legível por
> máquina**, os dados do transporte coletivo sob responsabilidade deste órgão:
>
> 1. Relação das linhas em operação (número, nome, operadora);
> 2. Itinerário de cada linha, por sentido (sequência de pontos de parada, com coordenadas, e o trajeto);
> 3. Localização dos pontos de parada;
> 4. Horários ou intervalos de operação.
>
> Caso exista, solicito preferencialmente o arquivo no padrão **GTFS (General Transit Feed Specification)**.
> Na falta dele, aceito planilhas (CSV/XLSX) ou arquivos KML/GeoJSON.
>
> Os dados serão usados num aplicativo gratuito e sem fins lucrativos de informação ao usuário do transporte
> público.
>
> Atenciosamente,
> [seu nome]

Quando receber os dados:
- arquivo **GTFS (.zip)** → coloque em `data/gtfs/` e faça commit;
- outro formato → peça ao Claude Code para converter para `data/linhas/*.json`.

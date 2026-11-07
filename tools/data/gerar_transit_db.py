#!/usr/bin/env python3
"""
Pipeline de dados do Vai de Buzão  ->  app/src/main/assets/transit.db

Fontes, em ordem de prioridade:
  1. GTFS oficial: qualquer arquivo  data/gtfs/*.zip  (se existir, substitui as linhas do OSM)
  2. OpenStreetMap via Overpass API: pontos (highway=bus_stop, platforms), relações de linha
     (type=route + route=bus) e lugares para busca offline (bairros, hospitais, terminais...).

Dados do OpenStreetMap: © colaboradores do OpenStreetMap, licença ODbL 1.0.

Uso:
  python3 tools/data/gerar_transit_db.py                 # baixa do Overpass e gera o banco
  python3 tools/data/gerar_transit_db.py --entrada x.json # usa um JSON do Overpass já baixado
  python3 tools/data/gerar_transit_db.py --cache          # reaproveita tools/data/cache/overpass.json

Só usa a biblioteca padrão do Python 3.9+.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import json
import math
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.error
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DB_OUT = ROOT / "app" / "src" / "main" / "assets" / "transit.db"
REPORT_OUT = ROOT / "docs" / "dados-relatorio.md"
CACHE = ROOT / "tools" / "data" / "cache" / "overpass.json"
GTFS_DIR = ROOT / "data" / "gtfs"

# Recorte: Região Metropolitana do Vale do Rio Cuiabá
# (Cuiabá, Várzea Grande, N. Sra. do Livramento, Sto. Antônio de Leverger, Chapada dos Guimarães, Acorizal)
BBOX = (-16.05, -56.55, -15.10, -55.55)  # sul, oeste, norte, leste

OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
USER_AGENT = "VaiDeBuzao-data-pipeline/1.0 (+https://github.com/inforprimetecnologiadev/vaidebuzao)"

STOP_ROLES = ("stop", "stop_entry_only", "stop_exit_only")
PLATFORM_ROLES = ("platform", "platform_entry_only", "platform_exit_only")
INFER_STOP_MAX_METERS = 25.0
SHAPE_SIMPLIFY_METERS = 4.0

PLACE_KINDS = {
    "suburb": "Bairro", "neighbourhood": "Bairro", "quarter": "Bairro",
    "city": "Cidade", "town": "Cidade", "village": "Localidade", "hamlet": "Localidade",
}
POI_KINDS = {
    ("amenity", "hospital"): "Hospital", ("amenity", "clinic"): "Saúde",
    ("amenity", "university"): "Ensino", ("amenity", "college"): "Ensino",
    ("amenity", "bus_station"): "Terminal", ("amenity", "townhall"): "Prefeitura",
    ("amenity", "marketplace"): "Mercado", ("shop", "mall"): "Shopping",
    ("leisure", "stadium"): "Estádio", ("leisure", "park"): "Parque",
    ("tourism", "attraction"): "Turismo", ("tourism", "museum"): "Museu",
    ("aeroway", "aerodrome"): "Aeroporto",
}


def overpass_query() -> str:
    s, w, n, e = BBOX
    b = f"({s},{w},{n},{e})"
    return f"""
[out:json][timeout:300];
relation["type"="route"]["route"~"^(bus|trolleybus|minibus)$"]{b}->.routes;
.routes out body;
(node(r.routes); way(r.routes);)->.members;
node.members;
out body;
way.members;
out geom;
(
  node["highway"="bus_stop"]{b};
  node["public_transport"="platform"]["bus"="yes"]{b};
  node["amenity"="bus_station"]{b};
)->.stops;
.stops out body;
node["place"~"^(suburb|neighbourhood|quarter|village|town|city|hamlet)$"]["name"]{b}->.places;
.places out body;
(
  nwr["amenity"~"^(hospital|clinic|university|college|bus_station|townhall|marketplace)$"]["name"]{b};
  nwr["shop"="mall"]["name"]{b};
  nwr["leisure"~"^(stadium|park)$"]["name"]{b};
  nwr["tourism"~"^(attraction|museum)$"]["name"]{b};
  nwr["aeroway"="aerodrome"]["name"]{b};
)->.pois;
.pois out center;
"""


def fetch_overpass() -> dict:
    data = urllib.parse.urlencode({"data": overpass_query()}).encode()
    last_err = None
    for attempt in range(3):
        for url in OVERPASS_ENDPOINTS:
            try:
                print(f"Baixando do Overpass: {url} (tentativa {attempt + 1})", flush=True)
                req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=360) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                if not payload.get("elements"):
                    raise RuntimeError("resposta vazia")
                return payload
            except urllib.error.HTTPError as exc:
                last_err = exc
                detail = exc.read().decode("utf-8", "replace")[:800]
                print(f"  falhou: {exc}\n{detail}", flush=True)
                if exc.code == 400:
                    raise SystemExit("Consulta Overpass inválida (HTTP 400).")
            except Exception as exc:  # noqa: BLE001
                last_err = exc
                print(f"  falhou: {exc}", flush=True)
                time.sleep(10 * (attempt + 1))
    raise SystemExit(f"Não foi possível baixar do Overpass: {last_err}")


# ----------------------------------------------------------------------------- geometria

def haversine(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_008.8 * math.asin(min(1.0, math.sqrt(h)))


def encode_polyline(points: list[tuple[float, float]]) -> str:
    out = []
    prev_lat = prev_lon = 0
    for lat, lon in points:
        ilat, ilon = round(lat * 1e5), round(lon * 1e5)
        for v in (ilat - prev_lat, ilon - prev_lon):
            v = ~(v << 1) if v < 0 else v << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        prev_lat, prev_lon = ilat, ilon
    return "".join(out)


def decode_polyline(encoded: str) -> list[tuple[float, float]]:
    """Decodifica "Encoded Polyline" (precisão 1e5), usado pelo app ao exportar uma linha gravada."""
    points, index, lat, lon = [], 0, 0, 0
    while index < len(encoded):
        for which in (0, 1):
            result = shift = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20 or index >= len(encoded):
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if which == 0:
                lat += delta
            else:
                lon += delta
        points.append((lat / 1e5, lon / 1e5))
    return points


def simplify(points: list[tuple[float, float]], tol_m: float) -> list[tuple[float, float]]:
    """Ramer–Douglas–Peucker em coordenadas projetadas localmente (metros)."""
    if len(points) < 3:
        return points
    lat0 = math.radians(points[0][0])
    kx, ky = 111_320.0 * math.cos(lat0), 110_540.0
    xy = [(p[1] * kx, p[0] * ky) for p in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i, j = stack.pop()
        (x1, y1), (x2, y2) = xy[i], xy[j]
        dx, dy = x2 - x1, y2 - y1
        norm = math.hypot(dx, dy) or 1e-9
        best, idx = 0.0, -1
        for k in range(i + 1, j):
            x0, y0 = xy[k]
            d = abs(dy * x0 - dx * y0 + x2 * y1 - y2 * x1) / norm
            if d > best:
                best, idx = d, k
        if best > tol_m and idx > 0:
            keep[idx] = True
            stack += [(i, idx), (idx, j)]
    return [p for p, k in zip(points, keep) if k]


class Grid:
    """Índice espacial simples em grade para buscas por proximidade."""

    def __init__(self, cell_deg: float = 0.002):
        self.cell = cell_deg
        self.cells: dict[tuple[int, int], list] = defaultdict(list)

    def key(self, lat: float, lon: float) -> tuple[int, int]:
        return int(math.floor(lat / self.cell)), int(math.floor(lon / self.cell))

    def add(self, lat: float, lon: float, item) -> None:
        self.cells[self.key(lat, lon)].append((lat, lon, item))

    def near(self, lat: float, lon: float, radius_m: float):
        r = int(math.ceil(radius_m / (self.cell * 111_000))) + 1
        ky, kx = self.key(lat, lon)
        for y in range(ky - r, ky + r + 1):
            for x in range(kx - r, kx + r + 1):
                for plat, plon, item in self.cells.get((y, x), ()):
                    d = haversine((lat, lon), (plat, plon))
                    if d <= radius_m:
                        yield d, item


def chain_ways(way_geoms: list[list[tuple[float, float]]]) -> list[tuple[float, float]]:
    """Une as vias da relação na ordem, invertendo cada uma quando necessário."""
    ways = [w for w in way_geoms if len(w) >= 2]
    if not ways:
        return []
    chain = list(ways[0])
    if len(ways) > 1:
        nxt = ways[1]
        d_end = min(haversine(chain[-1], nxt[0]), haversine(chain[-1], nxt[-1]))
        d_start = min(haversine(chain[0], nxt[0]), haversine(chain[0], nxt[-1]))
        if d_start < d_end:
            chain.reverse()
    for w in ways[1:]:
        if haversine(chain[-1], w[0]) <= haversine(chain[-1], w[-1]):
            chain.extend(w[1:] if chain[-1] == w[0] else w)
        else:
            rw = list(reversed(w))
            chain.extend(rw[1:] if chain[-1] == rw[0] else rw)
    return chain


def infer_stops_along(shape: list[tuple[float, float]], stop_grid: Grid) -> list:
    """Pontos a até 25 m do trajeto, em ordem de passagem (para linhas sem pontos cadastrados)."""
    found: dict = {}
    along = 0.0
    for i in range(len(shape) - 1):
        a, b = shape[i], shape[i + 1]
        seg = haversine(a, b)
        steps = max(1, int(seg // 15))
        for s in range(steps + 1):
            t = s / steps
            p = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            for d, key in stop_grid.near(p[0], p[1], INFER_STOP_MAX_METERS):
                pos = along + seg * t
                if key not in found or d < found[key][1]:
                    found[key] = (pos, d)
        along += seg
    ordered = sorted(found.items(), key=lambda kv: kv[1][0])
    out = []
    for key, (pos, _) in ordered:
        if out and out[-1][1] > pos - 30 and out[-1][0] == key:
            continue
        out.append((key, pos))
    return [k for k, _ in out]


# ----------------------------------------------------------------------------- OSM

def in_bbox(lat: float, lon: float) -> bool:
    south, west, north, east = BBOX
    return south <= lat <= north and west <= lon <= east


MANUAL_DIR = ROOT / "data" / "linhas"
SNAP_METERS = 35.0


def parse_headway(tags: dict) -> int | None:
    raw = tags.get("interval") or tags.get("headway")
    if not raw:
        return None
    m = re.match(r"^\s*(\d{1,2}):(\d{2})", raw)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2))
    m = re.match(r"^\s*(\d{1,3})", raw)
    return int(m.group(1)) if m else None


def build_from_osm(payload: dict):
    nodes: dict[int, dict] = {}
    ways: dict[int, dict] = {}
    relations: list[dict] = []
    places: list[dict] = []
    pois: list[dict] = []
    for el in payload.get("elements", []):
        t = el.get("type")
        tags = el.get("tags", {})
        if t == "node":
            nodes[el["id"]] = el
            if "place" in tags and tags.get("name"):
                places.append(el)
        elif t == "way":
            ways[el["id"]] = el
        elif t == "relation" and tags.get("type") == "route":
            relations.append(el)
        if tags.get("name") and any(tags.get(k) == v for (k, v) in POI_KINDS):
            pois.append(el)

    stops: dict[tuple, dict] = {}

    def stop_from_node(nid: int):
        n = nodes.get(nid)
        if not n or "lat" not in n or not in_bbox(n["lat"], n["lon"]):
            return None
        key = ("n", nid)
        if key not in stops:
            tags = n.get("tags", {})
            stops[key] = {"lat": n["lat"], "lon": n["lon"], "name": tags.get("name"), "ref": f"node/{nid}"}
        return key

    def stop_from_way(wid: int):
        w = ways.get(wid)
        if not w or not w.get("geometry"):
            return None
        key = ("w", wid)
        if key not in stops:
            g = w["geometry"]
            lat = sum(p["lat"] for p in g) / len(g)
            lon = sum(p["lon"] for p in g) / len(g)
            if not in_bbox(lat, lon):
                return None
            stops[key] = {"lat": lat, "lon": lon, "name": w.get("tags", {}).get("name"), "ref": f"way/{wid}"}
        return key

    # pontos avulsos (mesmo sem linha cadastrada aparecem como "ponto mais próximo")
    for nid, n in nodes.items():
        tags = n.get("tags", {})
        if tags.get("highway") == "bus_stop" or (tags.get("public_transport") == "platform" and tags.get("bus") == "yes"):
            stop_from_node(nid)

    stop_grid = Grid()
    for key, s in stops.items():
        stop_grid.add(s["lat"], s["lon"], key)

    patterns = []
    for rel in relations:
        tags = rel.get("tags", {})
        members = rel.get("members", [])
        platforms = [m for m in members if m.get("role", "") in PLATFORM_ROLES]
        stop_pos = [m for m in members if m.get("role", "") in STOP_ROLES]
        chosen = platforms if len(platforms) >= 2 else stop_pos
        # Linhas rodoviárias/interestaduais (ex.: Recife–Cuiabá) não são transporte urbano
        outside = 0
        for m in chosen:
            el = nodes.get(m["ref"]) if m["type"] == "node" else None
            if el and "lat" in el and not in_bbox(el["lat"], el["lon"]):
                outside += 1
        if chosen and outside / len(chosen) > 0.3:
            patterns.append({"skip": True, "tags": tags, "osm": rel["id"], "motivo": "fora da região (linha rodoviária)"})
            continue
        seq = []
        for m in chosen:
            key = stop_from_node(m["ref"]) if m["type"] == "node" else stop_from_way(m["ref"]) if m["type"] == "way" else None
            if key and (not seq or seq[-1] != key):
                seq.append(key)
        geoms = []
        for m in members:
            if m["type"] == "way" and m.get("role", "") in ("", "forward", "backward", "route"):
                w = ways.get(m["ref"])
                if w and w.get("geometry"):
                    geoms.append([(p["lat"], p["lon"]) for p in w["geometry"]])
        shape = [p for p in chain_ways(geoms) if in_bbox(p[0], p[1])]
        inferred = False
        if len(seq) < 2 and len(shape) >= 2:
            seq = infer_stops_along(shape, stop_grid)
            inferred = True
        if len(seq) < 2:
            patterns.append({"skip": True, "tags": tags, "osm": rel["id"]})
            continue
        if len(shape) < 2:
            shape = [(stops[k]["lat"], stops[k]["lon"]) for k in seq]
        patterns.append({
            "skip": False,
            "ref": (tags.get("ref") or "").strip(),
            "name": (tags.get("name") or "").strip() or f"Linha {tags.get('ref', '')}".strip(),
            "from": tags.get("from"),
            "to": tags.get("to"),
            "color": tags.get("colour"),
            "operator": tags.get("operator") or tags.get("network"),
            "headway": parse_headway(tags),
            "stops": seq,
            "shape": shape,
            "inferred": inferred,
            "source_ref": f"relation/{rel['id']}",
        })

    place_rows = []
    for p in places:
        tags = p.get("tags", {})
        kind = PLACE_KINDS.get(tags.get("place", ""), "Lugar")
        place_rows.append({"name": tags["name"], "kind": kind, "lat": p["lat"], "lon": p["lon"]})
    for el in pois:
        tags = el.get("tags", {})
        kind = next((v for (k, val), v in POI_KINDS.items() if tags.get(k) == val), "Lugar")
        if "lat" in el:
            lat, lon = el["lat"], el["lon"]
        elif "center" in el:
            lat, lon = el["center"]["lat"], el["center"]["lon"]
        else:
            continue
        place_rows.append({"name": tags["name"], "kind": kind, "lat": lat, "lon": lon})

    return stops, patterns, place_rows


# ----------------------------------------------------------------------------- GTFS

def build_from_gtfs(zip_path: Path):
    z = zipfile.ZipFile(zip_path)

    def rows(name):
        if name not in z.namelist():
            return []
        with z.open(name) as f:
            return list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig")))

    stops = {}
    for r in rows("stops.txt"):
        if r.get("location_type", "0") not in ("", "0"):
            continue
        stops[("g", r["stop_id"])] = {
            "lat": float(r["stop_lat"]), "lon": float(r["stop_lon"]),
            "name": r.get("stop_name") or None, "ref": f"gtfs/{r['stop_id']}",
        }
    routes = {r["route_id"]: r for r in rows("routes.txt")}
    trips = {r["trip_id"]: r for r in rows("trips.txt")}
    st_by_trip = defaultdict(list)
    first_dep = {}
    for r in rows("stop_times.txt"):
        st_by_trip[r["trip_id"]].append((int(r["stop_sequence"]), r["stop_id"]))
        if r.get("departure_time") and r["trip_id"] not in first_dep:
            first_dep[r["trip_id"]] = r["departure_time"]
    shapes = defaultdict(list)
    for r in rows("shapes.txt"):
        shapes[r["shape_id"]].append((int(r["shape_pt_sequence"]), float(r["shape_pt_lat"]), float(r["shape_pt_lon"])))
    freq = defaultdict(list)
    for r in rows("frequencies.txt"):
        freq[r["trip_id"]].append(int(r["headway_secs"]) // 60)

    def to_min(hms: str) -> int:
        h, m, *_ = (int(x) for x in hms.split(":"))
        return h * 60 + m

    groups = defaultdict(list)
    for tid, t in trips.items():
        groups[(t["route_id"], t.get("direction_id", "0"))].append(tid)
    patterns = []
    for (rid, direction), tids in groups.items():
        r = routes.get(rid, {})
        best = max(tids, key=lambda t: len(st_by_trip.get(t, [])))
        seq = [("g", sid) for _, sid in sorted(st_by_trip.get(best, [])) if ("g", sid) in stops]
        if len(seq) < 2:
            continue
        trip = trips[best]
        sh = [(la, lo) for _, la, lo in sorted(shapes.get(trip.get("shape_id", ""), []))]
        if len(sh) < 2:
            sh = [(stops[k]["lat"], stops[k]["lon"]) for k in seq]
        headway = None
        if freq.get(best):
            headway = round(sum(freq[best]) / len(freq[best]))
        else:
            deps = sorted(to_min(first_dep[t]) for t in tids if t in first_dep)
            if len(deps) >= 2 and deps[-1] > deps[0]:
                headway = max(3, round((deps[-1] - deps[0]) / (len(deps) - 1)))
        color = r.get("route_color")
        patterns.append({
            "skip": False,
            "ref": r.get("route_short_name", "").strip(),
            "name": (r.get("route_long_name") or r.get("route_short_name") or "").strip(),
            "from": None,
            "to": trip.get("trip_headsign") or None,
            "color": f"#{color}" if color and not color.startswith("#") else color,
            "operator": None,
            "headway": headway,
            "stops": seq,
            "shape": sh,
            "inferred": False,
            "source_ref": f"gtfs-route/{rid}/{direction}",
        })
    return stops, patterns


# ----------------------------------------------------------------------------- linhas manuais

def load_manual_lines(stops: dict, manual_dir: Path) -> list[dict]:
    """
    Linhas cadastradas pelo projeto em data/linhas/*.json (ver data/linhas/LEIA-ME.md).
    Cada ponto informado por coordenada é "grudado" no ponto do OSM mais próximo (até 35 m);
    se não houver, um ponto novo é criado.
    """
    if not manual_dir.exists():
        return []
    grid = Grid()
    for key, st in stops.items():
        grid.add(st["lat"], st["lon"], key)
    out = []
    for f in sorted(manual_dir.glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        for li in data if isinstance(data, list) else [data]:
            for si, sentido in enumerate(li.get("sentidos", [])):
                seq = []
                for pi, pt in enumerate(sentido.get("pontos", [])):
                    lat, lon = float(pt["lat"]), float(pt["lon"])
                    if not in_bbox(lat, lon):
                        raise SystemExit(f"{f.name}: ponto fora da região: {lat},{lon}")
                    near = min(grid.near(lat, lon, SNAP_METERS), default=None, key=lambda x: x[0])
                    if near:
                        key = near[1]
                        if pt.get("nome") and not stops[key].get("name"):
                            stops[key]["name"] = pt["nome"]
                    else:
                        key = ("m", f"{f.stem}:{li.get('numero')}:{si}:{pi}")
                        stops[key] = {"lat": lat, "lon": lon, "name": pt.get("nome"), "ref": f"manual/{f.name}"}
                        grid.add(lat, lon, key)
                    if not seq or seq[-1] != key:
                        seq.append(key)
                if len(seq) < 2:
                    continue
                shape = [(float(p[0]), float(p[1])) for p in sentido.get("trajeto", [])] or \
                        decode_polyline(sentido.get("trajeto_polyline", "")) or \
                        [(stops[k]["lat"], stops[k]["lon"]) for k in seq]
                out.append({
                    "skip": False,
                    "ref": str(li.get("numero", "")).strip(),
                    "name": li.get("nome") or f"Linha {li.get('numero', '')}",
                    "from": sentido.get("de"),
                    "to": sentido.get("para"),
                    "color": li.get("cor"),
                    "operator": li.get("operadora"),
                    "headway": sentido.get("intervalo_min") or li.get("intervalo_min"),
                    "stops": seq,
                    "shape": shape,
                    "inferred": False,
                    "source_ref": f"manual/{f.name}",
                })
    return out


# ----------------------------------------------------------------------------- saída

def assign_areas(stops: dict, places: list[dict]) -> None:
    grid = Grid(cell_deg=0.01)
    for p in places:
        if p["kind"] in ("Bairro", "Localidade", "Cidade"):
            grid.add(p["lat"], p["lon"], p["name"])
    for s in stops.values():
        best = min(grid.near(s["lat"], s["lon"], 3000), default=None, key=lambda x: x[0])
        s["area"] = best[1] if best else None


def write_db(stops: dict, patterns: list[dict], places: list[dict], source: str, out: Path) -> dict:
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    if tmp.exists():
        tmp.unlink()
    con = sqlite3.connect(tmp)
    cur = con.cursor()
    cur.executescript("""
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
        CREATE TABLE stops(id INTEGER PRIMARY KEY, name TEXT, lat REAL NOT NULL, lon REAL NOT NULL, area TEXT, source_ref TEXT);
        CREATE TABLE patterns(id INTEGER PRIMARY KEY, ref TEXT, name TEXT, from_name TEXT, to_name TEXT, color TEXT,
                              operator TEXT, headway_min INTEGER, inferred INTEGER, shape TEXT, source_ref TEXT);
        CREATE TABLE pattern_stops(pattern_id INTEGER, seq INTEGER, stop_id INTEGER, PRIMARY KEY(pattern_id, seq));
        CREATE TABLE places(id INTEGER PRIMARY KEY, name TEXT, kind TEXT, lat REAL, lon REAL);
    """)
    used_keys = {k for p in patterns if not p["skip"] for k in p["stops"]}
    ids = {}
    for i, (key, s) in enumerate(sorted(stops.items(), key=lambda kv: (kv[1]["lat"], kv[1]["lon"])), start=1):
        ids[key] = i
        cur.execute("INSERT INTO stops VALUES (?,?,?,?,?,?)",
                    (i, s.get("name"), round(s["lat"], 6), round(s["lon"], 6), s.get("area"), s.get("ref")))
    pid = 0
    for p in sorted((p for p in patterns if not p["skip"]), key=lambda p: (p["ref"], p["name"], p.get("to") or "")):
        pid += 1
        shape = encode_polyline(simplify(p["shape"], SHAPE_SIMPLIFY_METERS))
        cur.execute("INSERT INTO patterns VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    (pid, p["ref"], p["name"], p.get("from"), p.get("to"), p.get("color"), p.get("operator"),
                     p.get("headway"), 1 if p["inferred"] else 0, shape, p["source_ref"]))
        for seq, key in enumerate(p["stops"]):
            cur.execute("INSERT INTO pattern_stops VALUES (?,?,?)", (pid, seq, ids[key]))
    seen = set()
    plid = 0
    for pl in sorted(places, key=lambda x: (x["kind"], x["name"].strip().lower(), x["lat"], x["lon"])):
        k = (pl["name"].strip().lower(), pl["kind"])
        if k in seen:
            continue
        seen.add(k)
        plid += 1
        cur.execute("INSERT INTO places VALUES (?,?,?,?,?)", (plid, pl["name"].strip(), pl["kind"], round(pl["lat"], 6), round(pl["lon"], 6)))
    now = dt.datetime.now(dt.timezone.utc)
    meta = {
        "data_version": now.strftime("%Y%m%d.%H%M"),
        "generated_at": now.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "source": source,
        "bbox": ",".join(str(x) for x in BBOX),
        "stops": str(len(stops)),
        "stops_with_lines": str(len(used_keys)),
        "patterns": str(pid),
        "places": str(plid),
    }
    cur.executemany("INSERT INTO meta VALUES (?,?)", meta.items())
    cur.execute("CREATE INDEX idx_ps_stop ON pattern_stops(stop_id)")
    con.commit()
    con.execute("VACUUM")
    con.close()
    tmp.replace(out)
    return meta


def write_report(meta: dict, patterns: list[dict], out: Path) -> None:
    ok = [p for p in patterns if not p["skip"]]
    skipped = [p for p in patterns if p["skip"]]
    lines = defaultdict(list)
    for p in ok:
        lines[p["ref"] or p["name"]].append(p)

    def sort_key(ref: str):
        m = re.match(r"^(\d+)", ref)
        return (int(m.group(1)) if m else 10**9, ref)

    md = [
        "# Relatório de qualidade dos dados",
        "",
        "Gerado automaticamente por `tools/data/gerar_transit_db.py`. Não edite à mão.",
        "",
        f"- **Fonte:** {meta['source']}",
        f"- **Versão dos dados:** `{meta['data_version']}` (gerado em {meta['generated_at']})",
        f"- **Recorte (sul, oeste, norte, leste):** `{meta['bbox']}`",
        f"- **Pontos de ônibus:** {meta['stops']} (com pelo menos uma linha: {meta['stops_with_lines']})",
        f"- **Linhas distintas:** {len(lines)} · **sentidos/variantes:** {meta['patterns']}",
        f"- **Lugares para busca offline:** {meta['places']}",
        f"- **Sentidos com pontos inferidos pelo trajeto:** {sum(1 for p in ok if p['inferred'])}",
        f"- **Relações descartadas:** {len(skipped)}",
        "",
        "> Dados © colaboradores do OpenStreetMap (ODbL). Itinerários podem estar desatualizados ou incompletos.",
        "> Ajude a melhorar editando o OpenStreetMap: https://www.openstreetmap.org",
        "",
        "## Linhas",
        "",
        "| Linha | Nome | Sentidos | Pontos (por sentido) | Observação |",
        "|---|---|---|---|---|",
    ]
    for ref in sorted(lines, key=sort_key):
        ps = lines[ref]
        obs = []
        if any(p["inferred"] for p in ps):
            obs.append("pontos inferidos")
        if any(len(p["stops"]) < 5 for p in ps):
            obs.append("poucos pontos")
        if not ps[0]["ref"]:
            obs.append("sem número")
        name = ps[0]["name"].replace("|", "/")
        counts = ", ".join(str(len(p["stops"])) for p in ps)
        md.append(f"| {ref} | {name} | {len(ps)} | {counts} | {', '.join(obs)} |")
    if skipped:
        md += ["", "## Relações descartadas", ""]
        for p in skipped[:200]:
            t = p["tags"]
            motivo = p.get("motivo", "sem pontos nem trajeto")
            md.append(f"- relation/{p['osm']}: {t.get('ref', '')} {t.get('name', '')} — {motivo}")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(md) + "\n", encoding="utf-8")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--entrada", type=Path, help="JSON do Overpass já baixado")
    ap.add_argument("--cache", action="store_true", help="reaproveitar tools/data/cache/overpass.json")
    ap.add_argument("--saida", type=Path, default=DB_OUT)
    ap.add_argument("--relatorio", type=Path, default=REPORT_OUT)
    ap.add_argument("--gtfs-dir", type=Path, default=GTFS_DIR)
    ap.add_argument("--linhas-dir", type=Path, default=MANUAL_DIR)
    args = ap.parse_args(argv)

    if args.entrada:
        payload = json.loads(args.entrada.read_text(encoding="utf-8"))
    elif args.cache and CACHE.exists():
        payload = json.loads(CACHE.read_text(encoding="utf-8"))
    else:
        payload = fetch_overpass()
        CACHE.parent.mkdir(parents=True, exist_ok=True)
        CACHE.write_text(json.dumps(payload), encoding="utf-8")

    stops, patterns, places = build_from_osm(payload)
    source = "OpenStreetMap (© colaboradores do OpenStreetMap, ODbL)"

    gtfs_files = sorted(args.gtfs_dir.glob("*.zip")) if args.gtfs_dir.exists() else []
    if gtfs_files:
        g_stops, g_patterns = {}, []
        for f in gtfs_files:
            s, p = build_from_gtfs(f)
            g_stops.update(s)
            g_patterns += p
        if g_patterns:
            print(f"GTFS encontrado ({', '.join(f.name for f in gtfs_files)}): substitui as linhas do OSM.")
            stops, patterns = g_stops, g_patterns
            source = f"GTFS ({', '.join(f.name for f in gtfs_files)}) + lugares do OpenStreetMap (ODbL)"

    manual = load_manual_lines(stops, args.linhas_dir)
    if manual:
        print(f"Linhas cadastradas manualmente: {len(manual)} sentidos")
        patterns += manual
        source += " + linhas cadastradas pelo projeto (data/linhas)"

    assign_areas(stops, places)
    meta = write_db(stops, patterns, places, source, args.saida)
    write_report(meta, patterns, args.relatorio)
    print(json.dumps(meta, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())

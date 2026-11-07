"""Testes do pipeline de dados com um JSON fictício no formato do Overpass.

Rodar:  python3 -m unittest discover -s tools/data/tests
"""
import importlib.util
import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("pipeline", HERE.parent / "gerar_transit_db.py")
pipeline = importlib.util.module_from_spec(spec)
sys.modules["pipeline"] = pipeline
spec.loader.exec_module(pipeline)


def node(i, lat, lon, **tags):
    return {"type": "node", "id": i, "lat": lat, "lon": lon, "tags": tags}


def way(i, pts, **tags):
    return {"type": "way", "id": i, "geometry": [{"lat": a, "lon": b} for a, b in pts], "tags": tags}


FIXTURE = {
    "elements": [
        # Linha 101 (PTv2): pontos como platforms, vias em ordem (a segunda via desenhada ao contrário)
        {"type": "relation", "id": 900, "tags": {"type": "route", "route": "bus", "ref": "101", "name": "101 Centro - CPA",
                                                 "from": "Centro", "to": "CPA", "interval": "00:15"},
         "members": [
             {"type": "node", "ref": 1, "role": "platform"},
             {"type": "node", "ref": 2, "role": "platform"},
             {"type": "node", "ref": 3, "role": "platform"},
             {"type": "way", "ref": 50, "role": ""},
             {"type": "way", "ref": 51, "role": ""},
         ]},
        # Linha 202 (PTv1): só vias, pontos devem ser inferidos
        {"type": "relation", "id": 901, "tags": {"type": "route", "route": "bus", "ref": "202", "name": "202 Várzea Grande"},
         "members": [{"type": "way", "ref": 52, "role": ""}]},
        # Linha rodoviária interestadual: deve ser descartada (pontos fora da região)
        {"type": "relation", "id": 903, "tags": {"type": "route", "route": "bus", "ref": "11-0010", "name": "Recife - Cuiabá"},
         "members": [{"type": "node", "ref": 80, "role": "platform"}, {"type": "node", "ref": 81, "role": "platform"},
                     {"type": "node", "ref": 1, "role": "platform"}]},
        node(80, -8.05, -34.90, highway="bus_stop", name="Terminal do Recife"),
        node(81, -16.68, -49.25, highway="bus_stop", name="Terminal de Goiânia"),
        # Relação inútil (sem nada)
        {"type": "relation", "id": 902, "tags": {"type": "route", "route": "bus", "ref": "999"}, "members": []},
        node(1, -15.6000, -56.1000, highway="bus_stop", name="Praça Alencastro"),
        node(2, -15.6000, -56.0900, highway="bus_stop"),
        node(3, -15.6000, -56.0800, highway="bus_stop", name="Shopping"),
        node(4, -15.6500, -56.1300, highway="bus_stop", name="Centro VG"),
        node(5, -15.6500, -56.1200, highway="bus_stop", name="Avenida da FEB"),
        node(6, -15.7000, -56.3000, highway="bus_stop", name="Ponto avulso"),
        node(7, -15.6010, -56.0950, place="suburb", name="Centro Norte"),
        way(50, [(-15.6000, -56.1000), (-15.6000, -56.0900)]),
        way(51, [(-15.6000, -56.0800), (-15.6000, -56.0900)]),
        way(52, [(-15.6500, -56.1300), (-15.6500, -56.1250), (-15.6500, -56.1200)]),
        {"type": "way", "id": 60, "center": {"lat": -15.61, "lon": -56.09}, "tags": {"amenity": "hospital", "name": "Hospital Teste"}},
    ]
}


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        src = self.tmp / "overpass.json"
        src.write_text(json.dumps(FIXTURE))
        self.db = self.tmp / "transit.db"
        self.report = self.tmp / "relatorio.md"
        linhas = self.tmp / "linhas"
        linhas.mkdir()
        (linhas / "teste.json").write_text(json.dumps([{
            "numero": "500", "nome": "500 Teste Manual", "operadora": "Operadora X", "intervalo_min": 30,
            "sentidos": [{"de": "Centro", "para": "Shopping", "pontos": [
                {"lat": -15.60001, "lon": -56.10002},                       # gruda na Praça Alencastro
                {"lat": -15.6200, "lon": -56.0700, "nome": "Ponto Novo"},  # cria ponto novo
            ]}],
        }]))
        rc = pipeline.main(["--entrada", str(src), "--saida", str(self.db), "--relatorio", str(self.report),
                            "--gtfs-dir", str(self.tmp / "sem-gtfs"), "--linhas-dir", str(linhas)])
        self.assertEqual(rc, 0)
        self.con = sqlite3.connect(self.db)

    def tearDown(self):
        self.con.close()

    def q(self, sql, *args):
        return self.con.execute(sql, args).fetchall()

    def test_linhas_e_pontos(self):
        pats = self.q("SELECT ref, to_name, headway_min, inferred FROM patterns ORDER BY ref")
        self.assertEqual(pats, [("101", "CPA", 15, 0), ("202", None, None, 1), ("500", "Shopping", 30, 0)])
        names = self.q("""SELECT s.name FROM pattern_stops ps JOIN stops s ON s.id = ps.stop_id
                          JOIN patterns p ON p.id = ps.pattern_id WHERE p.ref='101' ORDER BY ps.seq""")
        self.assertEqual([n for (n,) in names], ["Praça Alencastro", None, "Shopping"])
        inferred = self.q("""SELECT s.name FROM pattern_stops ps JOIN stops s ON s.id = ps.stop_id
                             JOIN patterns p ON p.id = ps.pattern_id WHERE p.ref='202' ORDER BY ps.seq""")
        self.assertEqual([n for (n,) in inferred], ["Centro VG", "Avenida da FEB"])

    def test_ponto_avulso_e_bairro(self):
        # 6 do OSM + 1 criado pela linha manual; os pontos de Recife e Goiânia ficam de fora
        self.assertEqual(self.q("SELECT COUNT(*) FROM stops")[0][0], 7)
        self.assertEqual(self.q("SELECT COUNT(*) FROM stops WHERE name LIKE 'Terminal%'")[0][0], 0)
        area = self.q("SELECT area FROM stops WHERE name='Praça Alencastro'")[0][0]
        self.assertEqual(area, "Centro Norte")

    def test_lugares_e_meta(self):
        kinds = dict(self.q("SELECT name, kind FROM places"))
        self.assertEqual(kinds["Hospital Teste"], "Hospital")
        self.assertEqual(kinds["Centro Norte"], "Bairro")
        meta = dict(self.q("SELECT key, value FROM meta"))
        self.assertEqual(meta["patterns"], "3")
        self.assertIn("OpenStreetMap", meta["source"])
        report = self.report.read_text()
        self.assertIn("relation/902", report)
        self.assertIn("relation/903", report)
        self.assertIn("fora da região", report)

    def test_linha_manual_gruda_no_ponto_osm(self):
        names = self.q("""SELECT s.name FROM pattern_stops ps JOIN stops s ON s.id = ps.stop_id
                          JOIN patterns p ON p.id = ps.pattern_id WHERE p.ref='500' ORDER BY ps.seq""")
        self.assertEqual([n for (n,) in names], ["Praça Alencastro", "Ponto Novo"])

    def test_trajeto_unido_na_ordem(self):
        shape = self.q("SELECT shape FROM patterns WHERE ref='101'")[0][0]
        self.assertTrue(len(shape) > 0)

    def test_chain_ways_inverte(self):
        chain = pipeline.chain_ways([[(0, 1), (0, 0)], [(0, 1), (0, 2)]])
        self.assertEqual(chain, [(0, 0), (0, 1), (0, 2)])


if __name__ == "__main__":
    unittest.main()

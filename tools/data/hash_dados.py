#!/usr/bin/env python3
"""Imprime um hash do conteúdo do transit.db, ignorando a tabela meta (datas/versão).
Usado pelo workflow para só commitar quando os dados de verdade mudaram."""
import hashlib
import sqlite3
import sys

h = hashlib.sha256()
try:
    con = sqlite3.connect(sys.argv[1])
    for (table,) in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name != 'meta' ORDER BY name"):
        h.update(table.encode())
        for row in con.execute(f"SELECT * FROM {table} ORDER BY 1, 2"):
            h.update(repr(row).encode())
except Exception:  # noqa: BLE001 — arquivo ausente ou inválido conta como "diferente"
    h.update(b"invalido")
print(h.hexdigest())

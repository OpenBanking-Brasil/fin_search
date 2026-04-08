"""
Pesquisa texto nas blobs da tabela `records` da base SQLite do Notion (Windows).

Uso:
  python _tmp_query_notion.py
  python _tmp_query_notion.py --needle "meu texto" --max 10
"""

from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
from pathlib import Path


def _safe_columns(cols: list[str]) -> list[str]:
    """Só nomes de coluna alfanuméricos (evita injeção em SQL dinâmico)."""
    return [c for c in cols if re.match(r"^[A-Za-z0-9_]+$", c)]


def main() -> int:
    parser = argparse.ArgumentParser(description="Pesquisa na notion.db (Notion desktop).")
    parser.add_argument(
        "--db",
        type=Path,
        default=None,
        help="Caminho para notion.db (predef.: %%APPDATA%%/Notion/notion.db)",
    )
    parser.add_argument(
        "--needle",
        default=r"Desvira|desvira|livro|O_DESVIRA",
        help="Expressão regular (re.IGNORECASE).",
    )
    parser.add_argument("--max", type=int, default=5, help="Máximo de correspondências a mostrar.")
    args = parser.parse_args()

    db_path = args.db
    if db_path is None:
        appdata = os.environ.get("APPDATA", "")
        if not appdata:
            print("ERRO: APPDATA não definido; use --db explicitamente.", file=sys.stderr)
            return 1
        db_path = Path(appdata) / "Notion" / "notion.db"

    if not db_path.is_file():
        print(f"ERRO: base não encontrada: {db_path}", file=sys.stderr)
        return 1

    try:
        conn = sqlite3.connect(str(db_path))
    except sqlite3.Error as e:
        print(f"ERRO ao abrir SQLite: {e}", file=sys.stderr)
        return 1

    cur = conn.cursor()
    try:
        cols = [r[1] for r in cur.execute("PRAGMA table_info(records)")]
    except sqlite3.Error as e:
        print(f"ERRO: tabela 'records' inexistente ou inacessível: {e}", file=sys.stderr)
        conn.close()
        return 1

    print("records columns:", cols)
    blob_cols = _safe_columns(
        ["record_value"] if "record_value" in cols else [x for x in cols if x not in ("id",)]
    )
    if not blob_cols:
        print("ERRO: nenhuma coluna segura para ler.", file=sys.stderr)
        conn.close()
        return 1

    needle = re.compile(args.needle, re.I)
    found: list[str] = []

    for col in blob_cols:
        try:
            q = f'SELECT "{col}" FROM records'
            for row in cur.execute(q):
                blob = row[0]
                if blob is None:
                    continue
                if isinstance(blob, bytes):
                    s = blob.decode("utf-8", errors="ignore")
                else:
                    s = str(blob)
                if needle.search(s):
                    found.append(s[:800])
                    if len(found) >= args.max:
                        break
        except sqlite3.Error as ex:
            print("skip col", col, ex, file=sys.stderr)
        if len(found) >= args.max:
            break

    conn.close()
    print("matches", len(found))
    for i, s in enumerate(found):
        print("---", i + 1, "---")
        print(s[:800])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

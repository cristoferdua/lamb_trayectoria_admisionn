"""Genera la guia unica (sesiones S1-S16) pegando el codigo REAL y probado de las carpetas de ejemplo.

Uso (desde la carpeta guias-web):  python generar.py

  plantillas/sesiones/*.md  -> docs/sesiones/*.md      (guia unica: agregar un servicio a Lamb, con o sin CRUD)
  plantillas/proyecto-sello/*.md -> docs/proyecto-sello/*.md (guia: crear un servicio desde cero, con o sin CRUD)

  {{lamb:ruta}}  -> archivo de  Lamb-Modulo02-con-Biblioteca  (ejemplo probado)
  {{file:ruta}}  -> archivo de  Servicio-Desde-Cero
"""
import pathlib
import re
import sys

BASE = pathlib.Path(r"C:\Users\crist\Documents\lamb uopeu 2.0")
COPIA = BASE / "Lamb-Modulo02-con-Biblioteca"
ORIGEN = {"lamb": COPIA, "file": BASE / "Servicio-Desde-Cero"}
AQUI = pathlib.Path(__file__).parent
WEB = AQUI / "docs"
PLANT = AQUI / "plantillas"

LANG = {".yml": "yaml", ".yaml": "yaml", ".json": "json", ".xml": "xml", ".java": "java",
        ".sql": "sql", ".html": "html", ".ts": "ts", ".gitignore": "text"}


def bloque(tipo: str, rel: str) -> str:
    p = ORIGEN[tipo] / rel
    txt = p.read_text(encoding="utf-8").replace("\r\n", "\n").rstrip("\n")
    lang = LANG.get(p.suffix or p.name, "text")
    return f"```{lang}\n{txt}\n```"


def render(f: pathlib.Path) -> str:
    txt = f.read_text(encoding="utf-8")
    txt = re.sub(r"\{\{(lamb|file):([^}]+)\}\}", lambda m: bloque(m.group(1), m.group(2)), txt)
    if re.search(r"\{\{(lamb|file):", txt):
        sys.exit(f"placeholder sin resolver en {f.name}")
    return txt


for carpeta in ("sesiones", "proyecto-sello"):
    for f in sorted((PLANT / carpeta).glob("*.md")):
        destino = WEB / carpeta / f.name
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(render(f), encoding="utf-8", newline="\n")
        print("ok", destino)

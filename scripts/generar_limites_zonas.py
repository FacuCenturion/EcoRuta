"""
Genera src/data/limites-zonas.json con el contorno de cada zona de higiene urbana.

Cada zona se arma uniendo las comunas que le corresponden según el mapa del
Ente Único Regulador de los Servicios Públicos (Servicio de Higiene Urbana - Zonas).
Los límites de barrios y comunas salen de los datos abiertos del GCBA.

Uso:
    pip install shapely
    python scripts/generar_limites_zonas.py
"""

import json
import urllib.request
from pathlib import Path

from shapely.geometry import shape
from shapely.ops import unary_union

URL_BARRIOS = "https://raw.githubusercontent.com/OpenDataCordoba/barrios/main/caba_barrios.geojson"
SALIDA = Path(__file__).resolve().parent.parent / "src" / "data" / "limites-zonas.json"

# Comunas que cubre cada zona
COMUNAS_POR_ZONA = {
    1: [1],
    2: [2, 13, 14],
    3: [11, 12, 15],
    4: [9, 10],
    5: [8],
    6: [5, 6, 7],
    7: [3, 4],
}

TOLERANCIA = 0.00008  # unos 8 metros, para que el archivo no pese tanto
DECIMALES = 5


def bajar_barrios():
    with urllib.request.urlopen(URL_BARRIOS) as respuesta:
        return json.load(respuesta)


def a_puntos(anillo):
    # GeoJSON guarda [lng, lat]; en la app usamos [lat, lng]
    return [[round(lat, DECIMALES), round(lng, DECIMALES)] for lng, lat in anillo.coords]


def main():
    barrios = bajar_barrios()["features"]
    zonas = []

    for zona_id, comunas in COMUNAS_POR_ZONA.items():
        partes = [shape(b["geometry"]) for b in barrios if int(b["properties"]["COMUNA"]) in comunas]
        # El buffer de ida y vuelta cierra las rendijas que quedan entre barrios vecinos
        union = unary_union(partes).buffer(0.00005).buffer(-0.00005)
        poligonos = list(getattr(union, "geoms", [union]))

        # Se descartan pedacitos sueltos (islotes, muelles) que no aportan al mapa
        mayor = max(p.area for p in poligonos)
        poligonos = [p for p in poligonos if p.area > mayor * 0.01]

        zonas.append({
            "id": zona_id,
            "comunas": comunas,
            "poligonos": [a_puntos(p.simplify(TOLERANCIA).exterior) for p in poligonos],
        })

    SALIDA.write_text(json.dumps({"fuente": URL_BARRIOS, "zonas": zonas}, ensure_ascii=False))
    print(f"Listo: {SALIDA}")


if __name__ == "__main__":
    main()

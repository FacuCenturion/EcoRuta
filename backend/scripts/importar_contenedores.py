"""Arma la migración con los contenedores reales de la zona 1 (Comuna 1).

De dónde salen los datos:
- Ubicaciones: planilla "Ubicación contenedores RSU - fracción húmedos" (marzo 2025), que el
  Gobierno de la Ciudad entregó adjunta a la nota NO-2025-19872282-GCABA-DGLIM, en respuesta a
  un pedido de acceso a la información. Tiene zona, calle, altura, tipo, comuna y barrio.
- Coordenadas: el normalizador de direcciones de la Ciudad (USIG), que ubica cada dirección
  sobre el eje de la calle.

La propia Ciudad aclara que la planilla puede no coincidir del todo con la vía pública,
porque los contenedores se mueven. Para la demo alcanza.

Uso (desde la raíz del repo, tarda un par de minutos por la geocodificación):
    pip install openpyxl pypdf
    python backend/scripts/importar_contenedores.py

Genera:
    backend/scripts/datos/contenedores_zona1.csv           la muestra con sus coordenadas
    backend/src/main/resources/db/migration/V7__contenedores_reales_zona_1.sql

Con la misma semilla sale siempre la misma muestra. Si la planilla o la muestra cambian, no se
edita V7: se genera una migración nueva.
"""

import csv
import io
import json
import random
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

import openpyxl
import pypdf

URL_NOTA = "https://buenosaires.gob.ar/sites/default/files/2025-07/NO-2025-19872282-GCABA-DGLIM.pdf"
ADJUNTO = "Ubicacion contenedores RSU - fraccion humedos.xlsx"
URL_USIG = "https://servicios.usig.buenosaires.gob.ar/normalizar/"

RAIZ = Path(__file__).resolve().parents[2]
LIMITES = RAIZ / "src" / "data" / "limites-zonas.json"
SALIDA_CSV = RAIZ / "backend" / "scripts" / "datos" / "contenedores_zona1.csv"
SALIDA_SQL = RAIZ / "backend" / "src" / "main" / "resources" / "db" / "migration" / "V7__contenedores_reales_zona_1.sql"

SEMILLA = 2026

# Cada camión trabaja un sector. Los barrios se agrupan por cómo se usan: eso es lo que
# después cambia el patrón de llenado (oficinas, gastronómico, residencial).
SECTORES = [
    (1, "Retiro y San Nicolás", ["RETIRO", "SAN NICOLAS"], "C22"),
    (2, "Monserrat", ["MONSERRAT"], "C31"),
    (3, "San Telmo y Puerto Madero", ["SAN TELMO", "PUERTO MADERO"], "C14"),
    (4, "Constitución", ["CONSTITUCION"], "C17"),
]
# Cuántos contenedores se toman de cada barrio y con qué prefijo se nombran
MUESTRA = {
    "RETIRO": (25, "RET"),
    "SAN NICOLAS": (30, "SNI"),
    "MONSERRAT": (40, "MON"),
    "SAN TELMO": (30, "STE"),
    "PUERTO MADERO": (15, "PMA"),
    "CONSTITUCION": (40, "CON"),
}
NOMBRE_BARRIO = {
    "RETIRO": "Retiro", "SAN NICOLAS": "San Nicolás", "MONSERRAT": "Monserrat",
    "SAN TELMO": "San Telmo", "PUERTO MADERO": "Puerto Madero", "CONSTITUCION": "Constitución",
}

# Capacidad de cada tipo y la velocidad de llenado con la que arranca el sistema (% por hora),
# antes de aprender la real de cada contenedor. Un contenedor chico se llena antes.
TIPOS = {
    "LATERAL": ("lateral", 3200, 2.5),
    "BILATERAL": ("bilateral", 1100, 5.0),
    "SOTERRADO": ("soterrado", 3000, 2.0),
}

PALABRAS_CHICAS = {"de", "del", "la", "las", "los", "y", "el"}


def descargar_planilla():
    print("Bajando la nota de la Ciudad...")
    with urllib.request.urlopen(URL_NOTA, timeout=60) as respuesta:
        pdf = pypdf.PdfReader(io.BytesIO(respuesta.read()))
    contenido = pdf.attachments[ADJUNTO][0]
    hoja = openpyxl.load_workbook(io.BytesIO(contenido), read_only=True).worksheets[0]
    filas = list(hoja.iter_rows(values_only=True))
    encabezado = filas[0]
    return [dict(zip(encabezado, fila)) for fila in filas[1:]]


def sin_acentos(texto):
    return "".join(c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn")


# La planilla viene en mayúsculas y sin acentos. Estas son las palabras que aparecen en la zona 1.
ACENTOS = {
    "CORDOBA": "Córdoba", "RIOS": "Ríos", "PERON": "Perón", "SAENZ": "Sáenz", "MEJIA": "Mejía",
    "RODRIGUEZ": "Rodríguez", "MARTIN": "Martín", "HIPOLITO": "Hipólito", "BARTOLOME": "Bartolomé",
    "RAMON": "Ramón", "ECHAGUE": "Echagüe", "GUEMES": "Güemes", "ANTARTIDA": "Antártida", "JOSE": "José",
    "MARIA": "María", "HERNAN": "Hernán", "AIME": "Aimé", "COLON": "Colón", "GARCIA": "García",
    "TACUARI": "Tacuarí", "MAIPU": "Maipú", "PEÑA": "Peña", "PEÑALOZA": "Peñaloza", "BOLIVAR": "Bolívar",
    "ALVAREZ": "Álvarez", "LOPEZ": "López", "GOMEZ": "Gómez", "PEREZ": "Pérez", "SANCHEZ": "Sánchez",
    "ACHAVAL": "Achával", "OLGA": "Olga", "VELEZ": "Vélez", "SARSFIELD": "Sarsfield", "ARAOZ": "Aráoz",
}


def capitalizar(palabra, primera):
    minuscula = palabra.lower()
    if not primera and minuscula in PALABRAS_CHICAS:
        return minuscula
    if palabra.upper() in ACENTOS:
        return ACENTOS[palabra.upper()]
    return palabra[:1].upper() + palabra[1:].lower()


def nombre_de_calle(calle):
    """
    La planilla escribe 'APELLIDO, NOMBRE, TÍTULO' con la avenida al final. Se reordena como
    en el cartel de la calle: 'MADERO, EDUARDO AV.' -> 'Av. Eduardo Madero',
    'FELS, PABLO T.,CABO' -> 'Cabo Pablo T. Fels', 'CEVALLOS, Virrey' -> 'Virrey Cevallos'.
    """
    calle = " ".join(calle.split())
    avenida = calle.upper().endswith(" AV.")
    if avenida:
        calle = calle[: -len(" AV.")]
    if "DIAGONAL NORTE" in calle.upper():
        calle, avenida = "DIAGONAL NORTE", False
    elif "DIAGONAL SUR" in calle.upper():
        calle, avenida = "DIAGONAL SUR", False

    partes = [p.strip() for p in calle.split(",") if p.strip()]
    if len(partes) >= 2:
        # los títulos (Dr., Pres., Cabo...) van primero, después el nombre y al final el apellido
        partes = partes[2:] + [partes[1], partes[0]]
    palabras = " ".join(partes).split()
    nombre = " ".join(capitalizar(p, i == 0 and not avenida) for i, p in enumerate(palabras))
    return f"Av. {nombre}" if avenida else nombre


def geocodificar(calle, altura):
    parametros = urllib.parse.urlencode(
        {"direccion": f"{calle} {altura}, CABA", "geocodificar": "true", "srid": "4326"})
    try:
        with urllib.request.urlopen(f"{URL_USIG}?{parametros}", timeout=20) as respuesta:
            datos = json.load(respuesta)
    except Exception:
        return None
    encontradas = datos.get("direccionesNormalizadas") or []
    if not encontradas or not encontradas[0].get("coordenadas"):
        return None
    coordenadas = encontradas[0]["coordenadas"]
    return float(coordenadas["y"]), float(coordenadas["x"])


def dentro_de(punto, poligono):
    """Ray casting: si el punto (lat, lng) cae dentro del polígono [[lat, lng], ...]."""
    lat, lng = punto
    adentro = False
    j = len(poligono) - 1
    for i in range(len(poligono)):
        yi, xi = poligono[i][0], poligono[i][1]
        yj, xj = poligono[j][0], poligono[j][1]
        if (yi > lat) != (yj > lat) and lng < (xj - xi) * (lat - yi) / (yj - yi) + xi:
            adentro = not adentro
        j = i
    return adentro


def poligonos_zona_1():
    zonas = json.loads(LIMITES.read_text(encoding="utf-8"))["zonas"]
    return next(z for z in zonas if z["id"] == 1)["poligonos"]


def elegir_muestra(filas):
    azar = random.Random(SEMILLA)
    poligonos = poligonos_zona_1()
    elegidos = []
    for barrio, (cantidad, prefijo) in MUESTRA.items():
        candidatos = sorted(
            (f for f in filas if f["ZONA"] == 1 and f["BARRIO"] == barrio and f["ALTURA"]),
            key=lambda f: (f["CALLE"], f["ALTURA"]))
        azar.shuffle(candidatos)
        vistos = set()
        numero = 0
        for fila in candidatos:
            if numero == cantidad:
                break
            clave = (fila["CALLE"], fila["ALTURA"])
            if clave in vistos:
                continue  # dos contenedores en la misma dirección: alcanza con uno
            vistos.add(clave)
            punto = geocodificar(fila["CALLE"], fila["ALTURA"])
            time.sleep(0.2)  # no saturar el servicio de la Ciudad
            if punto is None or not any(dentro_de(punto, p) for p in poligonos):
                continue
            numero += 1
            elegidos.append({
                "id": f"{prefijo}-{numero:02d}",
                "direccion": f"{nombre_de_calle(fila['CALLE'])} {fila['ALTURA']}",
                "barrio": NOMBRE_BARRIO[barrio],
                "sector": next(s[0] for s in SECTORES if barrio in s[2]),
                "tipo": fila["TIPO_MOBIL"],
                "lat": punto[0],
                "lng": punto[1],
            })
        print(f"  {NOMBRE_BARRIO[barrio]}: {numero} de {cantidad}")
        if numero < cantidad:
            raise SystemExit(f"No alcanzaron las direcciones geocodificadas en {barrio}")
    # El nivel con el que arranca la demo: la mayoría a medio llenar y unos pocos casi llenos
    for c in elegidos:
        c["llenado"] = round(azar.uniform(90, 99) if azar.random() < 0.1 else azar.uniform(15, 88))
    return elegidos


def texto(valor):
    return "'" + str(valor).replace("'", "''") + "'"


def escribir_csv(contenedores):
    SALIDA_CSV.parent.mkdir(parents=True, exist_ok=True)
    with SALIDA_CSV.open("w", newline="", encoding="utf-8") as archivo:
        columnas = ["id", "direccion", "barrio", "sector", "tipo", "lat", "lng", "llenado"]
        escritor = csv.DictWriter(archivo, fieldnames=columnas)
        escritor.writeheader()
        escritor.writerows(contenedores)


def escribir_sql(contenedores):
    camiones_que_quedan = [s[3] for s in SECTORES]
    lineas = [
        "-- Generado con backend/scripts/importar_contenedores.py. No editar a mano.",
        "-- El sistema pasa a trabajar solo con la zona 1 (Comuna 1), con contenedores reales:",
        "-- ubicaciones de la planilla oficial de la Ciudad (marzo 2025), geocodificadas con USIG.",
        "-- Las otras 6 zonas siguen existiendo para el mapa, sin contenedores ni camiones.",
        "",
        "-- Se borra lo que pasó en la demo (lecturas, rutas, incidentes) para poder sacar los contenedores viejos",
        "TRUNCATE notificacion, parada, ruta, incidente, lectura_sensor, patron_llenado RESTART IDENTITY;",
        "UPDATE estado_sistema SET reloj_at = NULL, en_pausa = FALSE, velocidad = 1 WHERE id = 1;",
        "",
        "-- Cada camión trabaja un sector de la zona",
        "CREATE TABLE sector (",
        "    id       INTEGER PRIMARY KEY,",
        "    zona_id  INTEGER NOT NULL REFERENCES zona (id),",
        "    nombre   VARCHAR(60) NOT NULL,",
        "    barrios  TEXT[] NOT NULL",
        ");",
        "INSERT INTO sector (id, zona_id, nombre, barrios) VALUES",
        ",\n".join(
            f"    ({s[0]}, 1, {texto(s[1])}, '{{{','.join(NOMBRE_BARRIO[b] for b in s[2])}}}')" for s in SECTORES) + ";",
        "",
        "-- Los contenedores de ejemplo se reemplazan por los reales",
        "DELETE FROM contenedor;",
        "ALTER TABLE contenedor ADD COLUMN sector_id INTEGER REFERENCES sector (id);",
        "ALTER TABLE contenedor ADD COLUMN barrio VARCHAR(40);",
        "ALTER TABLE contenedor ADD COLUMN tipo VARCHAR(20) CHECK (tipo IN ('lateral', 'bilateral', 'soterrado'));",
        "ALTER TABLE contenedor ADD COLUMN capacidad_litros INTEGER CHECK (capacidad_litros > 0);",
        "INSERT INTO contenedor (id, direccion, zona_id, sector_id, barrio, tipo, capacidad_litros, lat, lng,",
        "                        velocidad_llenado, llenado, llenado_inicial) VALUES",
    ]
    filas = []
    for c in contenedores:
        tipo, capacidad, velocidad = TIPOS[c["tipo"]]
        filas.append(
            f"    ({texto(c['id'])}, {texto(c['direccion'])}, 1, {c['sector']}, {texto(c['barrio'])}, "
            f"{texto(tipo)}, {capacidad}, {c['lat']:.6f}, {c['lng']:.6f}, {velocidad}, {c['llenado']}, {c['llenado']})")
    lineas.append(",\n".join(filas) + ";")
    lineas += [
        "ALTER TABLE contenedor ALTER COLUMN sector_id SET NOT NULL;",
        "ALTER TABLE contenedor ALTER COLUMN barrio SET NOT NULL;",
        "ALTER TABLE contenedor ALTER COLUMN tipo SET NOT NULL;",
        "ALTER TABLE contenedor ALTER COLUMN capacidad_litros SET NOT NULL;",
        "CREATE INDEX idx_contenedor_sector ON contenedor (sector_id);",
        "",
        "-- Quedan 4 camiones, uno por sector, todos saliendo de la base de la zona 1",
        "ALTER TABLE camion ADD COLUMN sector_id INTEGER REFERENCES sector (id);",
        f"DELETE FROM camion WHERE id NOT IN ({', '.join(texto(c) for c in camiones_que_quedan)});",
        "DELETE FROM dispositivo WHERE usuario_id IN (SELECT id FROM usuario WHERE rol = 'chofer'",
        "    AND id NOT IN (SELECT chofer_id FROM camion WHERE chofer_id IS NOT NULL));",
        "DELETE FROM usuario WHERE rol = 'chofer' AND id NOT IN (SELECT chofer_id FROM camion WHERE chofer_id IS NOT NULL);",
    ]
    for sector_id, _, _, camion in SECTORES:
        lineas.append(
            f"UPDATE camion SET zona_id = 1, sector_id = {sector_id}, estado = 'en_base', carga_pct = 0, "
            f"km_recorridos_hoy = 0, lat = (SELECT base_lat FROM zona WHERE id = 1), "
            f"lng = (SELECT base_lng FROM zona WHERE id = 1) WHERE id = {texto(camion)};")
    lineas += [
        "ALTER TABLE camion ALTER COLUMN sector_id SET NOT NULL;",
        "",
    ]
    SALIDA_SQL.write_text("\n".join(lineas), encoding="utf-8")


def main():
    filas = descargar_planilla()
    print(f"Planilla: {len(filas)} contenedores. Geocodificando la muestra de la zona 1...")
    contenedores = elegir_muestra(filas)
    escribir_csv(contenedores)
    escribir_sql(contenedores)
    print(f"Listo: {len(contenedores)} contenedores.\n  {SALIDA_CSV}\n  {SALIDA_SQL}")


if __name__ == "__main__":
    main()

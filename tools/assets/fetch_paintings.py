"""Scarica la raccolta di dipinti di pubblico dominio per la tecnica Quadro.

Le opere vengono dall'Art Institute of Chicago, che pubblica in CC0 le riproduzioni delle opere di
pubblico dominio. Si scaricano a 1280 px sul lato lungo e si convertono in WebP: e' la stessa
qualita' che si vede su un telefono, a un quinto del peso di un JPEG a piena risoluzione.

Lo script e' idempotente: rieseguirlo riscarica e riscrive paintings.json.
"""
import io
import json
import os
import urllib.request

from PIL import Image

from picks import PICKS

# L'API rifiuta le richieste anonime (403), ed e' una richiesta esplicita dei loro termini: chi
# scarica dice chi e'.
HEADERS = {
    "User-Agent": "CodexApp/0.1 (https://github.com/Casual76/Codex; progetto personale)",
    "AIC-User-Agent": "CodexApp/0.1 (progetto personale)",
}

API = "https://api.artic.edu/api/v1/artworks/{}?fields=id,title,artist_title,date_display,image_id"
IIIF = "https://www.artic.edu/iiif/2/{}/full/1280,/0/default.jpg"
OUT_DIR = os.path.join("app", "src", "main", "assets", "paintings")


def fetch(url, timeout):
    return urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=timeout)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    catalogue, total = [], 0
    for object_id, slug, italian_title in PICKS:
        try:
            meta = json.load(fetch(API.format(object_id), 40))["data"]
            raw = fetch(IIIF.format(meta["image_id"]), 120).read()
            image = Image.open(io.BytesIO(raw)).convert("RGB")
            width, height = image.size
            scale = 1280 / max(width, height)
            if scale < 1:
                image = image.resize((round(width * scale), round(height * scale)), Image.LANCZOS)
            path = os.path.join(OUT_DIR, slug + ".webp")
            image.save(path, "WEBP", quality=82, method=6)
            size = os.path.getsize(path)
            total += size
            catalogue.append({
                "id": slug,
                "file": slug + ".webp",
                "title": italian_title,
                "originalTitle": meta["title"],
                "artist": meta.get("artist_title") or "Ignoto",
                "year": meta.get("date_display") or "",
                "museum": "Art Institute of Chicago",
                "license": "CC0 (pubblico dominio)",
                "source": "https://www.artic.edu/artworks/{}".format(object_id),
                "width": image.size[0],
                "height": image.size[1],
            })
            print("ok {:<20} {}x{} {} KB".format(slug, image.size[0], image.size[1], size // 1024))
        except Exception as error:  # noqa: BLE001 - lo script deve dire cosa non ha preso e proseguire
            print("ERRORE", slug, repr(error)[:120])
    with open(os.path.join(OUT_DIR, "paintings.json"), "w", encoding="utf-8") as handle:
        json.dump(catalogue, handle, ensure_ascii=False, indent=2)
    print("TOTALE {} opere, {:.1f} MB".format(len(catalogue), total / 1024 / 1024))


if __name__ == "__main__":
    main()

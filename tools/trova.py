# -*- coding: utf-8 -*-
"""Dove sta, sullo schermo, l'elemento che contiene un certo testo.

Legge un dump di `uiautomator` e stampa il centro di ogni nodo il cui testo o la cui descrizione
contengono la stringa cercata, uno per riga, dal piu' piccolo al piu' grande: il primo e' quasi
sempre proprio quello che si voleva toccare, e non il contenitore che lo circonda.

    python tools/trova.py tools/frames-out/ui.xml "rivela tutto"
"""
import re
import sys
import xml.etree.ElementTree as ElementTree

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def main() -> int:
    if len(sys.argv) < 3:
        print("uso: trova.py <dump.xml> <testo>", file=sys.stderr)
        return 2
    path, needle = sys.argv[1], sys.argv[2].lower()

    try:
        tree = ElementTree.parse(path)
    except (OSError, ElementTree.ParseError) as error:
        print("dump illeggibile: %s" % error, file=sys.stderr)
        return 1

    found = []
    for node in tree.iter():
        haystack = " ".join(
            node.get(name, "") for name in ("text", "content-desc", "resource-id")
        ).lower()
        if needle not in haystack:
            continue
        match = BOUNDS.match(node.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = (int(value) for value in match.groups())
        area = (x2 - x1) * (y2 - y1)
        if area <= 0:
            continue
        found.append((area, (x1 + x2) // 2, (y1 + y2) // 2))

    # Il piu' stretto per primo: e' il nodo che porta davvero quel testo, non la scheda intorno.
    for _, x, y in sorted(found):
        print(x, y)
    return 0 if found else 1


if __name__ == "__main__":
    raise SystemExit(main())

# -*- coding: utf-8 -*-
"""Le opere della raccolta: id dell'Art Institute of Chicago, nome del file, titolo italiano.

Sta in un file suo perche' e' l'unica parte che si tocca: aggiungere un dipinto vuol dire
aggiungere una riga qui, e `fetch_paintings.py` fa il resto.

**Criteri.** Paesaggi e marine di pubblico dominio, il piu' diversi possibile fra loro: un olio
olandese del Seicento, un inchiostro cinese Ming, una bufera di Turner e delle ninfee di Monet
danno quattro coperture che a colpo d'occhio non si confondono. E' quello che serve alla tecnica
Quadro, dove il dipinto e' la copertina di un messaggio: due sigilli che si somigliano sono due
sigilli che non dicono niente.

Nessuna riproduzione da musei statali italiani: il Codice dei beni culturali limita il riuso anche
delle opere in pubblico dominio.
"""

PICKS = [
    # La prima raccolta.
    (4783, "monet-poppy", "Campo di papaveri (Giverny)"),
    (81546, "monet-creuse", "La Petite Creuse"),
    (16487, "cezanne-marsiglia", "La baia di Marsiglia da L'Estaque"),
    (39554, "courbet-alpi", "Scena alpina"),
    (27027, "courbet-roccia", "La roccia di Hautepierre"),
    (90048, "cole-niagara", "Veduta lontana delle cascate del Niagara"),
    (76571, "church-cotopaxi", "Veduta del Cotopaxi"),
    (4770, "hobbema-bosco", "Paesaggio boscoso con casolare e cavaliere"),
    (95993, "diaz-stagno", "Stagno nel bosco"),
    (53058, "morisot-foresta", "Foresta di Compiègne"),
    (44017, "bazille-chailly", "Paesaggio a Chailly"),
    (495, "zeeman-costa", "Scena costiera"),
    (81557, "renoir-marina", "Marina"),
    (5292, "canaletto-terrazza", "La terrazza"),
    (28146, "dore-alpi", "Scena alpina"),
    (109926, "vuillard-finestra", "Paesaggio: finestra sul bosco"),

    # Seconda raccolta: piu' secoli, piu' climi, piu' tavolozze.
    (3546, "courbet-puits-noir", "La valle di Puits-Noir"),
    (863, "velde-pastorale", "Paesaggio pastorale con rovine"),
    (238272, "ruysdael-naarden", "Paesaggio fluviale con veduta di Naarden"),
    (110767, "momper-montagna", "Paesaggio montano"),
    (180711, "wright-salerno", "Il golfo di Salerno"),
    (93450, "goya-inverno", "Scena d'inverno"),
    (109938, "turner-aosta", "Valle d'Aosta: bufera, valanga e temporale"),
    (146701, "bierstadt-torrente", "Torrente di montagna"),
    (62393, "lafarge-neve", "Campo di neve, mattino"),
    (72801, "twachtman-ghiaccio", "Prigioniero del ghiaccio"),
    (68792, "inness-mare", "Veduta di mare"),
    (14309, "whistler-costa", "Scena costiera, bagnanti"),
    (16568, "monet-ninfee", "Ninfee"),
    (81540, "monet-etretat", "La partenza delle barche, Étretat"),
    (81545, "monet-covoni", "Covoni (tramonto, effetto neve)"),
    (86998, "monet-sandvika", "Sandvika, Norvegia"),
    (66144, "sorolla-biarritz", "Scogli al faro, Biarritz"),
    (874, "cazin-paesaggio", "Paesaggio al crepuscolo"),
    (71771, "xie-inverno", "Paesaggio d'inverno"),
    (76267, "xiang-fiume", "Fiume e montagne"),
]

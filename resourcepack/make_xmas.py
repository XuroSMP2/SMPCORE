#!/usr/bin/env python3
"""
Erzeugt das XuroSMP WEIHNACHTS-Pack (XuroSMP-Weihnachten.zip): das aktuelle XuroSMP-Pack.zip (Crystal-Tools, Rüstung, Icons, Logo)
plus Weihnachts-Extras: rotes Geschenk-Chest-Modell (einzelne Truhe), Zuckerstange, Plätzchen, Weihnachtsstern.
Alle Texturen selbst gezeichnet. Aufruf:  python3 make_xmas.py   ->   XuroSMP-Weihnachten.zip + SHA1-Weihnachten.txt
"""
import hashlib, json, os, random, zipfile
import make_pack as mp
from make_pack import Img, rgb, from_grid, outline

OUT = os.path.dirname(os.path.abspath(__file__))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.set(x, y, c)


# ---------------------------------------------------------------- Item-Texturen 16x16
def present():
    im = Img(16, 16)
    red, light, dark = rgb('d12b2b'), rgb('f25555'), rgb('9c1c1c')
    white, shade = rgb('fbfbfb'), rgb('cfd6e8')
    gold, gdark = rgb('ffd24a'), rgb('c9962a')
    rect(im, 2, 8, 13, 14, red)
    rect(im, 12, 8, 13, 14, dark)
    rect(im, 1, 5, 14, 8, light)
    rect(im, 13, 5, 14, 8, red)
    rect(im, 7, 5, 8, 14, white)
    rect(im, 1, 6, 14, 6, rgb('f25555'))
    rect(im, 8, 5, 8, 14, shade)
    # Schleife
    for (x, y) in [(4, 3), (5, 2), (6, 2), (6, 3), (6, 4), (5, 4), (4, 4), (11, 3), (10, 2), (9, 2), (9, 3), (9, 4), (10, 4), (11, 4)]:
        im.set(x, y, gold)
    for (x, y) in [(5, 3), (10, 3)]:
        im.set(x, y, gdark)
    rect(im, 7, 3, 8, 4, gold)
    return outline(im, rgb('3d0a0a'))


def candy_cane():
    im = Img(16, 16)
    path = [(11, 14), (11, 13), (11, 12), (11, 11), (11, 10), (11, 9), (11, 8), (11, 7), (11, 6), (10, 5), (9, 4), (8, 3), (7, 3), (6, 4), (5, 5), (5, 6)]
    for i, (x, y) in enumerate(path):
        c = rgb('e12d3a') if (i // 2) % 2 == 0 else rgb('fbfbfb')
        im.set(x, y, c)
        im.set(x + 1 if i < 8 else x, y + (0 if i < 8 else 1), rgb('b01c28') if (i // 2) % 2 == 0 else rgb('d5dbe8'))
    return outline(im, rgb('4a0a12'))


def cookie():
    im = Img(16, 16)
    base, edge, hi = rgb('e2a762'), rgb('b87a3a'), rgb('f3c98a')
    for y in range(16):
        for x in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 30:
                im.set(x, y, edge if d > 22 else base)
    for (x, y) in [(5, 4), (6, 4), (4, 5)]:
        im.set(x, y, hi)
    # Zuckerguss-Stern in der Mitte
    for (x, y) in [(7, 5), (8, 5), (7, 6), (8, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (6, 8), (7, 8), (8, 8), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9), (5, 10), (6, 10), (9, 10), (10, 10)]:
        im.set(x, y, rgb('fbfbfb'))
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        im.set(x, y, rgb('e12d3a'))
    for (x, y) in [(4, 8), (11, 6), (10, 11), (5, 11)]:
        im.set(x, y, rgb('5a3318'))
    return outline(im, rgb('4a2a10'))


def star():
    im = from_grid([
        "................",
        ".......yy.......",
        ".......yy.......",
        "......yYyy......",
        "......yYyy......",
        "yyyyyyYyyyyyyyy.",
        ".yyyyyyyyyyyyy..",
        "..yyyyyyyyyyy...",
        "...yyyyyyyyy....",
        "....yyyyyyy.....",
        "....yyyyyyy.....",
        "...yyyy.yyyy....",
        "...yyy...yyy....",
        "..yy.......yy...",
        "................",
        "................"],
        {'y': rgb('ffd23f'), 'Y': rgb('fff6b0')})
    for (x, y) in [(10, 7), (9, 8), (10, 9), (8, 10), (9, 10), (10, 11), (11, 12), (12, 13)]:
        im.set(x, y, rgb('e0a312'))
    return outline(im, rgb('6b3d00'))


XTEX = {'xmas_present': present(), 'candy_cane': candy_cane(), 'xmas_cookie': cookie(), 'xmas_star': star()}
XMODELS = {  # Basis-Item -> [(custom_model_data, Modell)]
    'chest': [(20, 'xmas_present')], 'stick': [(21, 'candy_cane')], 'cookie': [(22, 'xmas_cookie')], 'nether_star': [(23, 'xmas_star')],
}


# ---------------------------------------------------------------- Einzel-Truhe als Geschenk (64x64, Standard-Layout)
def chest_texture():
    im = Img(64, 64)
    red, side, top, dark = rgb('c92a2a'), rgb('a82020'), rgb('e04545'), rgb('7d1616')
    white, shade = rgb('fbfbfb'), rgb('d6dcea')
    gold, gdark = rgb('ffd24a'), rgb('c9962a')
    # Deckel (14x5x14 bei 0,0) und Boden (14x10x14 bei 0,19)
    faces = {
        'lid_top': (14, 0, 27, 13, top), 'lid_in': (28, 0, 41, 13, dark),
        'lid_l': (0, 14, 13, 18, side), 'lid_f': (14, 14, 27, 18, red), 'lid_r': (28, 14, 41, 18, side), 'lid_b': (42, 14, 55, 18, red),
        'base_top': (14, 19, 27, 32, dark), 'base_bot': (28, 19, 41, 32, dark),
        'base_l': (0, 33, 13, 42, side), 'base_f': (14, 33, 27, 42, red), 'base_r': (28, 33, 41, 42, side), 'base_b': (42, 33, 55, 42, red),
    }
    for (x0, y0, x1, y1, c) in faces.values():
        rect(im, x0, y0, x1, y1, c)
    # Schleifenband: senkrecht über alle Seitenflächen, Kreuz auf dem Deckel
    for key in ('lid_l', 'lid_f', 'lid_r', 'lid_b', 'base_l', 'base_f', 'base_r', 'base_b'):
        x0, y0, x1, y1, _ = faces[key]
        rect(im, x0 + 6, y0, x0 + 7, y1, white)
        rect(im, x0 + 7, y0, x0 + 7, y1, shade)
    x0, y0, x1, y1, _ = faces['lid_top']
    rect(im, x0 + 6, y0, x0 + 7, y1, white)
    rect(im, x0, y0 + 6, x1, y0 + 7, white)
    rect(im, x0 + 6, y0 + 6, x0 + 7, y0 + 7, gold)
    rect(im, x0 + 5, y0 + 5, x0 + 8, y0 + 5, gold)
    # Verschluss (2x4x1 bei 0,0) in Gold
    rect(im, 0, 0, 6, 4, gold)
    rect(im, 1, 3, 2, 4, gdark)
    # Schneeflocken-Punkte auf dem Papier
    rnd = random.Random(5)
    for key in ('lid_f', 'lid_b', 'base_f', 'base_b', 'lid_top'):
        x0, y0, x1, y1, _ = faces[key]
        for _ in range(5):
            x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
            if im.get(x, y) in (red, top):
                im.set(x, y, rgb('ffb0b0'))
    return im


# ---------------------------------------------------------------- festliches TAB-Logo
def xmas_logo():
    im = mp.make_logo()
    pal = [(235, 60, 70), (70, 205, 100)]
    out = Img(im.w, im.h)
    sc = 2
    for ci in range(7):
        c = pal[ci % 2]
        for y in range(im.h):
            for x in range(ci * 6 * sc, ci * 6 * sc + 5 * sc + 1):
                p = im.get(x, y)
                if p[3] and p[0] > 100 and not (p[0] < 60):   # Buchstabenfläche (Schatten ist dunkel)
                    out.set(x, y, c + (255,))
                elif p[3]:
                    out.set(x, y, p)
    # Schnee auf den obersten Buchstaben-Pixeln
    for x in range(out.w):
        for y in range(out.h):
            p = out.get(x, y)
            if p[3] and p[0] > 100:
                if (x // 2) % 3 != 1:
                    out.set(x, y, (245, 250, 255, 255))
                break
    return out


def build():
    """Baut auf dem ECHTEN Pack (XuroSMP-Pack.zip, HD-Texturen aus dem Artwork-Sheet) auf und ergänzt nur die Weihnachts-Extras.
    So behalten Crystal-Tools, Rüstung, Icons und Logo ihr aktuelles Aussehen."""
    base_zip = os.path.join(OUT, 'XuroSMP-Pack.zip')
    files = {}
    with zipfile.ZipFile(base_zip) as z:
        for n in z.namelist():
            if not n.endswith('/'):
                files[n] = z.read(n)
    mc = json.loads(files['pack.mcmeta'].decode('utf-8'))
    mc['pack']['description'] = "§dXuroSMP §7Crystal-Pack §c+ Weihnachten\n§8xurosmp.daronlo.com"
    files['pack.mcmeta'] = json.dumps(mc, ensure_ascii=False, indent=2).encode('utf-8')

    for name, img in XTEX.items():
        files[f'assets/smp/textures/item/{name}.png'] = img.png()
        files[f'assets/smp/models/item/{name}.json'] = json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"smp:item/{name}"}}).encode()
    for base, entries in XMODELS.items():
        key = f'assets/minecraft/items/{base}.json'
        if key in files:
            raise SystemExit(f'{key} existiert schon im Basis-Pack: bitte Modelle zusammenführen')
        d = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                       "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
                       "entries": [{"threshold": t, "model": {"type": "minecraft:model", "model": f"smp:item/{m}"}} for t, m in entries]}}
        files[key] = json.dumps(d).encode()
    # Truhe: nur die einzelne Kiste wird zum Geschenk (Doppelkisten und Endertruhe bleiben normal)
    files['assets/minecraft/textures/entity/chest/normal.png'] = chest_texture().png()

    zpath = os.path.join(OUT, 'XuroSMP-Weihnachten.zip')
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            zi = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[name])
    sha = hashlib.sha1(open(zpath, 'rb').read()).hexdigest()
    open(os.path.join(OUT, 'SHA1-Weihnachten.txt'), 'w').write(sha + '\n')
    print('Weihnachts-Pack erstellt:', zpath, '| SHA-1:', sha, '|', len(files), 'Dateien')

    sheet = Img(640, 240)
    for y in range(sheet.h):
        for x in range(sheet.w):
            sheet.px[y][x] = (40, 22, 60, 255)
    for i, n in enumerate(['xmas_present', 'candy_cane', 'xmas_cookie', 'xmas_star']):
        sheet.blit(XTEX[n].scaled(6), 8 + i * 110, 8)
    sheet.blit(chest_texture(), 480, 8)
    open(os.path.join(OUT, 'preview-weihnachten.png'), 'wb').write(sheet.png())
    return sha


def xmas_star_icon():
    return XTEX['xmas_star'].scaled(8)


if __name__ == '__main__':
    build()

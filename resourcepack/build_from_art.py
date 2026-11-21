#!/usr/bin/env python3
"""
Erzeugt XuroSMP-Pack.zip aus dem Artwork-Sheet (art/xurosmp-sheet.png): schneidet die Icons aus,
skaliert sie und baut das Pack (Items, Scoreboard-/TAB-Icons, Logo). Benötigt Pillow, numpy, scipy.
Aufruf:  python3 build_from_art.py   ->  XuroSMP-Pack.zip + SHA1.txt
"""
import hashlib, io, json, os, zipfile
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
SHEET = os.path.join(HERE, 'art', 'xurosmp-sheet.png')
ITEM_PX = 128   # Auflösung der Item-Texturen (Pack ist damit "HD"; 16x16-Items bleiben daneben normal)

im = Image.open(SHEET).convert('RGBA')
alpha = np.array(im)[:, :, 3]


MASKS = {}   # Box -> Maske des Bauteils (damit Reste von Nachbar-Icons nicht mit in den Ausschnitt geraten)


def comps(y0, y1, x0, x1, thr=40, it=1, minarea=1500):
    m = alpha[y0:y1, x0:x1] > thr
    lab, _ = ndi.label(ndi.binary_dilation(m, iterations=it))
    out = []
    for i, s in enumerate(ndi.find_objects(lab)):
        if (lab[s] == i + 1).sum() >= minarea:
            box = (s[1].start + x0, s[0].start + y0, s[1].stop + x0, s[0].stop + y0)
            full = np.zeros(alpha.shape, bool)
            full[y0:y1, x0:x1] = ndi.binary_dilation(lab == i + 1, iterations=3)  # Glow am Rand behalten
            MASKS[box] = full
            out.append(box)
    return sorted(out)


def crop(box, size, pad=0.04, keep_ratio=True):
    """Box ausschneiden, quadratisch auffüllen und auf size x size skalieren."""
    x0, y0, x1, y1 = box
    src = im
    if box in MASKS:
        arr = np.array(im)
        arr[:, :, 3] = np.where(MASKS[box], arr[:, :, 3], 0)
        src = Image.fromarray(arr)
    c = src.crop((x0, y0, x1, y1))
    w, h = c.size
    s = int(max(w, h) * (1 + pad * 2))
    sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    sq.paste(c, ((s - w) // 2, (s - h) // 2))
    return sq.resize((size, size), Image.LANCZOS)


# ---- Segmentierung (Koordinaten stammen aus dem Sheet) ----
row1 = comps(0, 380, 0, 1672, it=6, minarea=8000)           # 6 Werkzeuge
row2 = comps(372, 720, 0, 1672, it=6, minarea=8000)          # Helm, Brustplatte, Hose+Stiefel, Bogen, Pfeil, Kristall
legs = comps(380, 720, 620, 1030, it=1, minarea=1500)        # Hose, Stiefel getrennt
bottom = comps(750, 880, 0, 1000, it=1, minarea=150)         # 9 kleine Icons
logo = comps(688, 941, 994, 1672, it=6, minarea=8000)[0]
assert len(row1) == 6 and len(legs) == 2 and len(bottom) == 9, (len(row1), len(legs), len(bottom))
helmet = row2[0]; chest = row2[1]
bow = [b for b in row2 if b[0] >= 1040 and b[2] - b[0] > 200][0]
arrow = [b for b in row2 if 1270 <= b[0] <= 1300 and b[2] - b[0] < 150][0]
core = row2[-1]

ITEMS = {  # Name -> Box
    'megahammer': row1[0], 'crystal_pickaxe': row1[1], 'crystal_sword': row1[2], 'crystal_axe': row1[3], 'hammer': row1[4], 'excavator': row1[5],
    'crystal_helmet': helmet, 'crystal_chestplate': chest, 'crystal_leggings': legs[0], 'crystal_boots': legs[1],
    'crystal_bow': bow, 'crystal_arrow': arrow, 'crystal_core': core,
}
ICON_ORDER = ['money', 'gems', 'stars', 'kills', 'deaths', 'clan', 'ping', 'keyall', 'time']
# Sheet-Reihenfolge unten: Münze, Edelstein, Stern, Totenkopf, Schwerter, Schild, WLAN, Schlüssel, Uhr
ICON_BOX = dict(zip(['money', 'gems', 'stars', 'deaths', 'kills', 'clan', 'ping', 'keyall', 'time'], bottom))

# Item-Zuordnung: Basis-Item -> [(custom_model_data, Modellname)]  (muss zu tools.yml "model:" passen)
ITEM_MODELS = {
    'netherite_pickaxe': [(1, 'crystal_pickaxe'), (9, 'hammer'), (11, 'megahammer')],
    'netherite_axe': [(2, 'crystal_axe')],
    'netherite_sword': [(3, 'crystal_sword')],
    'netherite_helmet': [(4, 'crystal_helmet')],
    'netherite_chestplate': [(5, 'crystal_chestplate')],
    'netherite_leggings': [(6, 'crystal_leggings')],
    'netherite_boots': [(7, 'crystal_boots')],
    'netherite_shovel': [(10, 'excavator')],
    'bow': [(1, 'crystal_bow')],
    'arrow': [(1, 'crystal_arrow')],
    'nether_star': [(1, 'crystal_core')],
}
HANDHELD = {'crystal_pickaxe', 'crystal_axe', 'crystal_sword', 'hammer', 'megahammer', 'excavator'}


def png(img):
    b = io.BytesIO(); img.save(b, 'PNG', optimize=True); return b.getvalue()


files = {}
files['pack.mcmeta'] = json.dumps({"pack": {
    "pack_format": 46,
    "supported_formats": {"min_inclusive": 46, "max_inclusive": 999},
    "min_format": 46, "max_format": 999,
    "description": "§dXuroSMP §7Resource-Pack"}}, ensure_ascii=False).encode('utf-8')

# pack.png = Logo
lg = crop(logo, 256, pad=0.02)
files['pack.png'] = png(lg.resize((128, 128), Image.LANCZOS))

# Item-Texturen + Modelle
for name, box in ITEMS.items():
    files[f'assets/smp/textures/item/{name}.png'] = png(crop(box, ITEM_PX))
    parent = 'minecraft:item/handheld' if name in HANDHELD else 'minecraft:item/generated'
    files[f'assets/smp/models/item/{name}.json'] = json.dumps({"parent": parent, "textures": {"layer0": f"smp:item/{name}"}}).encode()
for base, entries in ITEM_MODELS.items():
    d = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                   "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
                   "entries": [{"threshold": t, "model": {"type": "minecraft:model", "model": f"smp:item/{m}"}} for t, m in entries]}}
    files[f'assets/minecraft/items/{base}.json'] = json.dumps(d).encode()

# Scoreboard-/TAB-Icons: ein Streifen aus 9 Icons à 16x16 (Glyphen -), Logo als 
ICON = 64   # Textur hochauflösend, angezeigt wird sie 8 px hoch (height unten)
atlas = Image.new('RGBA', (ICON * len(ICON_ORDER), ICON), (0, 0, 0, 0))
for i, k in enumerate(ICON_ORDER):
    atlas.paste(crop(ICON_BOX[k], ICON, pad=0.0), (i * ICON, 0))
files['assets/smp/textures/font/icons.png'] = png(atlas)
x0, y0, x1, y1 = logo
lh = 16   # Anzeigehöhe in der TAB-Liste
lwid = max(1, round((x1 - x0) / (y1 - y0) * lh))
logo_img = Image.fromarray(np.where(MASKS[logo][..., None], np.array(im), 0).astype('uint8')).crop(logo).resize((lwid * 8, lh * 8), Image.LANCZOS)   # 8-fach hochauflösend
files['assets/smp/textures/font/logo.png'] = png(logo_img)
font = {"providers": [
    {"type": "bitmap", "file": "smp:font/icons.png", "ascent": 7, "height": 8, "chars": ["".join(chr(0xE000 + i) for i in range(len(ICON_ORDER)))]},
    {"type": "bitmap", "file": "smp:font/logo.png", "ascent": 12, "height": lh, "chars": [chr(0xE100)]},
]}
files['assets/minecraft/font/default.json'] = json.dumps(font).encode('ascii')

zpath = os.path.join(HERE, 'XuroSMP-Pack.zip')
with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in sorted(files):
        zi = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0)); zi.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(zi, files[name])
sha = hashlib.sha1(open(zpath, 'rb').read()).hexdigest()
open(os.path.join(HERE, 'SHA1.txt'), 'w').write(sha + '\n')
print('OK', len(files), 'Dateien,', os.path.getsize(zpath) // 1024, 'KB, SHA-1', sha)

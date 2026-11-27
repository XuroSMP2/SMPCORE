# XuroSMP Resource-Pack

Enthält (Minecraft 1.21.4, Java):
- lila **Crystal**-Ausrüstung (Helm, Brustplatte, Hose, Stiefel, Schwert, Axt, Spitzhacke), roter **Hammer**, oranger **Excavator**
- Icons für **Scoreboard** (Geld, Gems, Stars, Kills, Tode, Clan, Ping, Keyall, Zeit) und ein **XUROSMP-Logo** für die **TAB-Liste**

## Einrichten
1. `XuroSMP-Pack.zip` hosten, der Link muss **direkt** die Datei laden. Am einfachsten: diese Datei im **öffentlichen** GitHub-Repo lassen und den Raw-Link nutzen:
   `https://raw.githubusercontent.com/<DEIN-NAME>/<REPO>/main/resourcepack/XuroSMP-Pack.zip`
2. In `plugins/SMPCore/config.yml` unter `resourcepack:` eintragen: `enabled: true`, `url: "<Link>"` und `sha1` (steht in `SHA1.txt`).
3. Server neu starten oder `/smpcore reload`. Spieler bekommen das Pack beim Join automatisch.

## Hinweise
- Bedrock-Spieler können Java-Resource-Packs nicht laden. Sie sehen automatisch die normalen Symbole (Fallbacks aus der config.yml).
- Neu erzeugen/ändern: `python3 make_pack.py` (ändert die SHA-1, dann in der config.yml aktualisieren).
- Eigene Texturen: PNG-Dateien in der ZIP unter `assets/smp/textures/item/` ersetzen (16x16) und die SHA-1 neu berechnen.

## Weihnachts-Pack (XuroSMP-Weihnachten.zip)
Enthält alles aus dem normalen Pack plus: rotes **Geschenk-Modell für einzelne Truhen**, Zuckerstange, Weihnachtsplätzchen, Weihnachtsstern und ein festliches TAB-Logo.
1. `XuroSMP-Weihnachten.zip` hosten (wie das normale Pack, direkter Link) und in der `config.yml` unter `resourcepack.xmas` eintragen: `enabled: true`, `url`, `sha1` (steht in `SHA1-Weihnachten.txt`).
2. In der Saison (27.11.–06.01.) schickt der Server automatisch dieses Pack, sonst das normale.
3. Neu erzeugen: `python3 make_xmas.py` (ändert die SHA-1).
Item-Modelle (CustomModelData): 20 Geschenk (Truhe), 21 Zuckerstange (Stick), 22 Plätzchen (Cookie), 23 Weihnachtsstern (Nether Star).

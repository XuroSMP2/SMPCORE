package de.smpcore.gui;

import de.smpcore.upgrade.UpgradeManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /upgrades: dauerhafte Verbesserungen kaufen. */
public final class UpgradesMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public UpgradesMenu(Player v) { super(v, 36, "<aqua><bold>Upgrades"); }

    @Override protected String tag() { return "upgrades"; }

    @Override
    protected void build() {
        int i = 0;
        for (UpgradeManager.Def d : plugin.upgrades().all()) {
            if (i >= SLOTS.length) break;
            int lvl = plugin.upgrades().level(viewer.getUniqueId(), d);
            boolean max = lvl >= d.maxLevel();
            List<String> lore = new ArrayList<>(d.lore());
            lore.add("");
            lore.add("<gray>Stufe: <white>" + lvl + "<gray>/<white>" + d.maxLevel());
            lore.add("<gray>Effekt: <aqua>" + plugin.upgrades().effectText(d));
            if (max) lore.add("<green><bold>Maximal ausgebaut");
            else {
                lore.add("<gray>Nächste Stufe: <yellow>" + plugin.upgrades().fmtCost(d, d.cost(lvl)));
                lore.add("<yellow>Klicken zum Kaufen");
            }
            ItemStack shown = Items.make(d.icon(), Math.max(1, Math.min(64, lvl == 0 ? 1 : lvl)), d.name(), lore);
            if (max) set(SLOTS[i], shown);
            else set(SLOTS[i], shown, e -> go(new ConfirmMenu(viewer, "<aqua>Upgrade kaufen", shown,
                    () -> plugin.upgrades().buy(viewer, d), () -> new UpgradesMenu(viewer))));
            i++;
        }
        if (i == 0) set(13, Items.make(Material.BARRIER, "<red>Keine Upgrades konfiguriert"));
        set(31, Items.make(Material.BARRIER, "<gray>Schließen"), e -> closeLater());
        fill(Items.filler());
    }
}

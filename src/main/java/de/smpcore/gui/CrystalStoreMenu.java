package de.smpcore.gui;

import de.smpcore.economy.EconomyManager;
import de.smpcore.shop.CrystalShopManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Stars Shop (AFK-Währung) mit festem Layout: 4 Reihen, Slots aus crystalshop.yml, rote Scheibe unten = Schließen. */
public final class CrystalStoreMenu extends Menu {
    private final int close;

    /** Anzahl Reihen: mindestens 4, wächst mit dem höchsten Slot (letzte Reihe ist für "Zurück" frei). */
    private static int rows() {
        int max = 0;
        for (CrystalShopManager.Entry en : de.smpcore.SMPCore.get().crystalShop().entries()) max = Math.max(max, en.slot());
        return Math.max(4, Math.min(6, max / 9 + 2));
    }

    public CrystalStoreMenu(Player p) {
        super(p, rows() * 9, "<light_purple>Stars Shop");
        this.close = (rows() - 1) * 9 + 4;
    }

    @Override protected String tag() { return "crystalshop"; }

    @Override
    protected void build() {
        long bal = plugin.economy().crystals(viewer.getUniqueId());
        for (CrystalShopManager.Entry en : plugin.crystalShop().entries()) {
            if (en.slot() < 0 || en.slot() >= close - 4 || en.slot() == close) continue;
            List<String> lore = new ArrayList<>(en.lore());
            lore.add("");
            lore.add("<gray>Preis: <yellow>" + EconomyManager.fmtC(en.cost()));
            lore.add("<gray>Du hast: <white>" + EconomyManager.fmtC(bal));
            lore.add("<yellow>Klicken zum Kaufen");
            int shownAmount = en.type().equals("ITEM") && en.amount() > 1 && en.amount() <= 64 ? en.amount() : 1;
            ItemStack shown = Items.make(en.icon(), shownAmount, en.name(), lore);
            set(en.slot(), shown, e -> go(new ConfirmMenu(viewer, "<yellow>Kauf bestätigen", shown,
                    () -> plugin.crystalShop().purchase(viewer, en), () -> new CrystalStoreMenu(viewer))));
        }
        set(close, Items.make(Material.RED_STAINED_GLASS_PANE, "<red>Zurück"), e -> go(new ShopMenus.Main(viewer)));
    }
}

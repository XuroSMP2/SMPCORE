package de.smpcore.gui;

import de.smpcore.settings.SettingsManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /settings: persönliche Einstellungen per Klick an/aus. */
public final class SettingsMenu extends Menu {
    public SettingsMenu(Player v) { super(v, 36, "<aqua><bold>Einstellungen"); }

    @Override protected String tag() { return "settings"; }

    @Override
    protected void build() {
        int i = 0;
        for (SettingsManager.Def d : plugin.settings().all()) {
            if (i >= 8) break;
            int col = 9 + i;
            boolean locked = d.permission() != null && !viewer.hasPermission(d.permission());
            boolean on = plugin.settings().on(viewer.getUniqueId(), d.id()) && !locked;
            List<String> lore = new ArrayList<>(d.lore());
            lore.add("");
            lore.add(locked ? "<red>Kein Zugriff" : on ? "<green><bold>AN <gray>- klicken zum Ausschalten" : "<red><bold>AUS <gray>- klicken zum Einschalten");
            ItemStack icon = Items.make(d.icon(), (on ? "<green>" : "<red>") + d.name(), lore.toArray(new String[0]));
            ItemStack dye = Items.make(locked ? Material.BARRIER : on ? Material.LIME_DYE : Material.GRAY_DYE,
                    (on ? "<green>AN" : "<red>AUS"), "<gray>" + d.name());
            if (locked) { set(col, icon); set(col + 9, dye); }
            else {
                java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> act = e -> { plugin.settings().toggle(viewer, d.id()); refresh(); };
                set(col, icon, act);
                set(col + 9, dye, act);
            }
            i++;
        }
        set(31, Items.make(Material.BARRIER, "<gray>Schließen"), e -> closeLater());
        fill(Items.filler());
    }
}

package de.smpcore.gui;

import de.smpcore.advent.AdventManager;
import de.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Adventskalender: 24 Türchen. Nur das Türchen von heute lässt sich öffnen. */
public final class AdventMenu extends Menu {
    public AdventMenu(Player v) { super(v, 54, "<red><bold>🎄 Adventskalender"); }

    @Override
    protected void build() {
        AdventManager a = plugin.advent();
        AdventManager.Phase ph = a.phase();
        int today = a.doorToday();
        List<Integer> got = a.claimed(viewer.getUniqueId());

        for (int i = 0; i < 54; i++) set(i, Items.make(Material.RED_STAINED_GLASS_PANE, " "));
        for (int i : new int[]{1, 3, 5, 7, 46, 48, 50, 52}) set(i, Items.make(Material.GREEN_STAINED_GLASS_PANE, " "));

        for (int d = 1; d <= 24; d++) {
            int slot = (1 + (d - 1) / 6) * 9 + 2 + (d - 1) % 6;
            boolean claimed = got.contains(d);
            ItemStack it;
            if (claimed) {
                List<String> lore = new ArrayList<>();
                lore.add("<green>✔ Abgeholt");
                lore.add("");
                for (AdventManager.Reward r : a.rewards(d)) lore.add("<dark_gray>• " + r.label());
                it = Items.make(Material.LIME_STAINED_GLASS_PANE, d, "<green>Türchen " + d, lore);
            } else if (ph == AdventManager.Phase.RUNNING && d == today) {
                List<String> lore = new ArrayList<>();
                lore.add("<yellow>Heute offen!");
                lore.add("");
                for (AdventManager.Reward r : a.rewards(d)) lore.add("<gray>• " + r.label());
                lore.add("");
                lore.add("<green>Klicken zum Öffnen");
                lore.add("<dark_gray>Nur heute abholbar.");
                it = Items.make(Material.CHEST, d, "<red><bold>Türchen " + d + " <yellow>(HEUTE)", lore);
                ItemMeta m = it.getItemMeta();
                if (m != null) { m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            } else if (ph == AdventManager.Phase.BEFORE || d > today) {
                it = Items.make(Material.PURPLE_STAINED_GLASS_PANE, d, "<light_purple>Türchen " + d,
                        List.of("<gray>Öffnet sich am <white>" + d + ". Dezember<gray>.", "<dark_gray>Inhalt: ???"));
            } else {
                it = Items.make(Material.GRAY_STAINED_GLASS_PANE, d, "<dark_gray>Türchen " + d,
                        List.of("<red>✘ Verpasst", "<dark_gray>Türchen gehen nur am jeweiligen Tag auf."));
            }
            final int day = d;
            set(slot, it, e -> {
                if (plugin.advent().claim(viewer, day)) refresh();
                else viewer.playSound(viewer.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            });
        }

        List<String> info = new ArrayList<>();
        switch (ph) {
            case BEFORE -> info.add("<gray>Startet in <white>" + a.daysUntilStart() + " <gray>Tag(en) – am <white>1. Dezember");
            case RUNNING -> {
                info.add("<gray>Heute: <white>Türchen " + today);
                info.add("<gray>Geöffnet: <green>" + got.size() + "<gray>/24");
            }
            default -> info.add("<gray>Für dieses Jahr beendet. Frohes Fest!");
        }
        info.add("");
        info.add("<gray>Jeden Tag ein neues Türchen – komm täglich online!");
        info.add("<gray>Verpasste Türchen können <red>nicht<gray> nachgeholt werden.");
        set(4, Items.make(Material.NETHER_STAR, 1, "<gold><bold>Adventskalender", info));

        List<String> bonus = new ArrayList<>();
        bonus.add("<gray>Öffne <white>alle 24 Türchen<gray> – jedes an seinem Tag –");
        bonus.add("<gray>und erhalte am 24. Dezember:");
        for (AdventManager.Reward r : a.perfectRewards()) bonus.add("<dark_gray>• " + r.label());
        set(49, Items.make(Material.GOLDEN_APPLE, 1, "<gold><bold>Treue-Bonus", bonus));
        set(45, Items.make(Material.BARRIER, "<red>Schließen"), e -> closeLater());
    }
}

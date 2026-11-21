package de.smpcore.recipe;

import de.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;

/**
 * Ersetzt das Vanilla-Rezept der End Crystals (formlos, Reihenfolge egal).
 * Standard (auch ohne Eintrag in der config.yml): 3 Netherite-Schrott, 2 Diamanten, 1 Ghast-Träne, 2 Glas, 1 Enderauge.
 */
public final class EndCrystalRecipe {
    private static final List<String> DEFAULT = List.of("NETHERITE_SCRAP:3", "DIAMOND:2", "GHAST_TEAR:1", "GLASS:2", "ENDER_EYE:1");
    private final SMPCore plugin;
    private final NamespacedKey key;

    public EndCrystalRecipe(SMPCore plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "end_crystal_hard"); }

    public void load() {
        Bukkit.removeRecipe(key);
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("endcrystal-recipe");
        boolean enabled = c == null || c.getBoolean("enabled", true);
        if (!enabled) return;
        int amount = c == null ? 1 : Math.max(1, c.getInt("amount", 1));
        boolean removeVanilla = c == null || c.getBoolean("remove-vanilla", true);
        List<String> items = c != null && c.isList("items") ? c.getStringList("items") : DEFAULT;
        try {
            ShapelessRecipe r = new ShapelessRecipe(key, new ItemStack(Material.END_CRYSTAL, amount));
            int total = 0;
            for (String line : items) {
                String[] p = line.split(":");
                Material m = Material.matchMaterial(p[0].trim());
                int n = p.length > 1 ? Integer.parseInt(p[1].trim()) : 1;
                if (m == null || n < 1) { plugin.getLogger().warning("End-Crystal-Rezept: ungültige Zutat '" + line + "'"); return; }
                total += n;
                if (total > 9) { plugin.getLogger().warning("End-Crystal-Rezept: max. 9 Zutaten"); return; }
                r.addIngredient(n, m);
            }
            if (total == 0) return;
            if (removeVanilla) Bukkit.removeRecipe(NamespacedKey.minecraft("end_crystal"));
            Bukkit.addRecipe(r);
            plugin.getLogger().info("End-Crystal-Rezept aktiv (" + total + " Zutaten).");
        } catch (Exception ex) {
            plugin.getLogger().warning("End-Crystal-Rezept ungültig: " + ex.getMessage());
        }
    }
}

package de.smpcore.recipe;

import de.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.List;

/** Ersetzt das Vanilla-Rezept der End Crystals durch ein sehr schweres (config.yml: endcrystal-recipe). */
public final class EndCrystalRecipe {
    private static final NamespacedKey KEY_VANILLA = NamespacedKey.minecraft("end_crystal");
    private final SMPCore plugin;
    private final NamespacedKey key;

    public EndCrystalRecipe(SMPCore plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "end_crystal_hard"); }

    public void load() {
        Bukkit.removeRecipe(key);
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("endcrystal-recipe");
        if (c == null || !c.getBoolean("enabled", true)) return;
        try {
            List<String> shape = c.getStringList("shape");
            ConfigurationSection ing = c.getConfigurationSection("ingredients");
            if (shape.isEmpty() || shape.size() > 3 || ing == null) return;
            ShapedRecipe r = new ShapedRecipe(key, new ItemStack(Material.END_CRYSTAL, Math.max(1, c.getInt("amount", 1))));
            r.shape(shape.stream().map(l -> l.replace('.', ' ')).toArray(String[]::new));
            for (String k : ing.getKeys(false)) {
                Material m = Material.matchMaterial(ing.getString(k, ""));
                if (m == null || k.length() != 1) { plugin.getLogger().warning("End-Crystal-Rezept: ungültige Zutat " + k); return; }
                r.setIngredient(k.charAt(0), m);
            }
            if (c.getBoolean("remove-vanilla", true)) Bukkit.removeRecipe(KEY_VANILLA);
            Bukkit.addRecipe(r);
        } catch (Exception ex) {
            plugin.getLogger().warning("End-Crystal-Rezept ungültig: " + ex.getMessage());
        }
    }
}

package de.smpcore.upgrade;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/** Dauerhafte Spieler-Upgrades (upgrades.yml): Endertruhen-Reihen, Homes, AH-Angebote. */
public final class UpgradeManager {
    public record Def(String id, String name, Material icon, String effect, String currency, int maxLevel, int perLevel,
                      List<Long> costs, List<String> lore) {
        public long cost(int currentLevel) { return costs.isEmpty() ? 0 : costs.get(Math.min(currentLevel, costs.size() - 1)); }
    }

    private final SMPCore plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    public UpgradeManager(SMPCore plugin) { this.plugin = plugin; }

    public void load() {
        defs.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "upgrades.yml")).getConfigurationSection("upgrades");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            Material icon = Material.matchMaterial(c.getString("icon", "NETHER_STAR"));
            String effect = c.getString("effect", "").toUpperCase(Locale.ROOT);
            String cur = c.getString("currency", "MONEY").toUpperCase(Locale.ROOT);
            if (!List.of("EC_ROWS", "HOMES", "LISTINGS").contains(effect)) { plugin.getLogger().warning("Upgrade " + id + ": unbekannter effect '" + effect + "'"); continue; }
            if (!List.of("MONEY", "STARS", "GEMS").contains(cur)) { plugin.getLogger().warning("Upgrade " + id + ": unbekannte currency '" + cur + "'"); continue; }
            List<Long> costs = new ArrayList<>();
            for (Object o : c.getList("costs", List.of())) if (o instanceof Number n) costs.add(n.longValue());
            defs.put(id.toLowerCase(Locale.ROOT), new Def(id.toLowerCase(Locale.ROOT), c.getString("name", id), icon == null ? Material.NETHER_STAR : icon,
                    effect, cur, Math.max(1, c.getInt("max-level", 1)), Math.max(1, c.getInt("per-level", 1)), costs, c.getStringList("lore")));
        }
    }

    public Collection<Def> all() { return defs.values(); }
    public Def get(String id) { return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT)); }

    public int level(UUID id, Def d) {
        PlayerData pd = plugin.data().get(id);
        return pd == null ? 0 : pd.upgrades.getOrDefault(d.id(), 0);
    }

    /** Summe aller Boni dieses Effekts (z. B. "HOMES"). */
    public int bonus(UUID id, String effect) {
        PlayerData pd = plugin.data().get(id);
        if (pd == null || pd.upgrades.isEmpty()) return 0;
        int sum = 0;
        for (Def d : defs.values()) if (d.effect().equals(effect)) sum += Math.min(d.maxLevel(), pd.upgrades.getOrDefault(d.id(), 0)) * d.perLevel();
        return sum;
    }

    public String fmtCost(Def d, long amount) {
        return switch (d.currency()) { case "STARS" -> EconomyManager.fmtC(amount); case "GEMS" -> EconomyManager.fmtG(amount); default -> EconomyManager.fmt(amount); };
    }

    public String effectText(Def d) {
        return switch (d.effect()) {
            case "EC_ROWS" -> "+" + d.perLevel() + " Endertruhen-Reihe(n) <dark_gray>(max. 6 Reihen)";
            case "HOMES" -> "+" + d.perLevel() + " Home(s)";
            default -> "+" + d.perLevel() + " AH-Angebot(e)";
        };
    }

    /** Kauft die nächste Stufe. */
    public boolean buy(Player p, Def d) {
        PlayerData pd = plugin.data().get(p.getUniqueId());
        if (pd == null) return false;
        int lvl = pd.upgrades.getOrDefault(d.id(), 0);
        if (lvl >= d.maxLevel()) { Msg.error(p, "Dieses Upgrade ist schon auf der höchsten Stufe."); return false; }
        long cost = d.cost(lvl);
        UUID id = p.getUniqueId();
        boolean paid = switch (d.currency()) {
            case "STARS" -> plugin.economy().takeCrystals(id, cost);
            case "GEMS" -> plugin.economy().takeGems(id, cost);
            default -> plugin.economy().take(id, cost);
        };
        if (!paid) { Msg.error(p, "Dafür fehlt dir etwas: " + fmtCost(d, cost) + " nötig."); return false; }
        pd.upgrades.put(d.id(), lvl + 1);
        Msg.ok(p, "Upgrade gekauft: " + d.name() + " <green>Stufe " + (lvl + 1) + "/" + d.maxLevel());
        return true;
    }
}

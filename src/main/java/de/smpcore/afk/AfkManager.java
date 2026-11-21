package de.smpcore.afk;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import de.smpcore.util.Persist;
import java.io.File;
import java.util.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** AFK-Zone: Wer in der Zone steht, bekommt regelmäßig Stars (AFK-Währung). */
public final class AfkManager {
    private final SMPCore plugin;
    private final Map<UUID, Integer> seconds = new HashMap<>();

    /** Platzierte AFK-Blöcke: "welt;x;y;z" -> Besitzer-UUID. */
    private final Map<String, UUID> blocks = new HashMap<>();
    private final NamespacedKey blockKey;
    private final File blockFile;

    public AfkManager(SMPCore plugin) {
        this.plugin = plugin;
        this.blockKey = new NamespacedKey(plugin, "afk_block");
        this.blockFile = new File(plugin.getDataFolder(), "afkblocks.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(blockFile);
        for (String k : y.getKeys(false)) {
            try { blocks.put(k.replace('|', ';'), UUID.fromString(y.getString(k, ""))); } catch (IllegalArgumentException ignored) {}
        }
    }

    private static String key(Location l) { return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ(); }

    private void saveBlocks() {
        YamlConfiguration y = new YamlConfiguration();
        blocks.forEach((k, v) -> y.set(k.replace(';', '|'), v.toString()));
        Persist.write(plugin, blockFile, y.saveToString(), true);
    }

    public org.bukkit.Material blockMaterial() {
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(plugin.getConfig().getString("afk.block-material", "LODESTONE"));
        return m == null || !m.isBlock() ? org.bukkit.Material.LODESTONE : m;
    }

    public ItemStack blockItem() {
        ItemStack it = new ItemStack(blockMaterial());
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c("<!italic><light_purple><bold>AFK-Block"));
        m.lore(List.of(Msg.c("<!italic><gray>Platzieren und draufstellen:"), Msg.c("<!italic><gray>du zählst als AFK (Stars) – überall."),
                Msg.c("<!italic><dark_gray>Abbauen gibt den Block zurück.")));
        m.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isBlockItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(blockKey, PersistentDataType.BYTE);
    }

    public boolean isAfkBlock(Location l) { return l.getWorld() != null && blocks.containsKey(key(l)); }
    public int blocksOf(UUID id) { int n = 0; for (UUID u : blocks.values()) if (u.equals(id)) n++; return n; }
    public void addBlock(Location l, UUID owner) { blocks.put(key(l), owner); saveBlocks(); }
    public void removeBlock(Location l) { if (blocks.remove(key(l)) != null) saveBlocks(); }

    /** Steht der Spieler auf einem (eigenen) AFK-Block? */
    public boolean onBlock(Player p) {
        if (blocks.isEmpty()) return false;
        Location f = p.getLocation().clone().subtract(0, 0.2, 0);
        return isAfkBlock(f);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 20L);
    }

    public boolean inZone(Location l) {
        Location a = plugin.data().afk(1), b = plugin.data().afk(2);
        if (a == null || b == null || a.getWorld() == null || l.getWorld() != a.getWorld() || a.getWorld() != b.getWorld()) return false;
        double minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY()) + 2;
        double minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        return l.getX() >= minX && l.getX() <= maxX && l.getY() >= minY && l.getY() <= maxY && l.getZ() >= minZ && l.getZ() <= maxZ;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;
        int interval = Math.max(5, plugin.getConfig().getInt("afk.interval-seconds", 60));
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean zone = inZone(p.getLocation());
            if (!zone && !onBlock(p)) { seconds.remove(p.getUniqueId()); continue; }
            int s = seconds.merge(p.getUniqueId(), 1, Integer::sum);
            if (s >= interval) {
                seconds.put(p.getUniqueId(), 0);
                reward(p);
            } else {
                p.sendActionBar(Msg.c((zone ? "<yellow>AFK-Zone" : "<light_purple>AFK-Block") + " <dark_gray>» <gray>nächste Stars in <white>" + (interval - s) + "s"));
            }
        }
    }

    private void reward(Player p) {
        long base = Math.max(1, plugin.getConfig().getLong("afk.crystals", 1));
        int bonus = plugin.ranks().afkBonus(p);
        long amount = Math.max(1, Math.round(base * (1 + bonus / 100.0)));
        plugin.economy().addCrystals(p.getUniqueId(), amount);
        String prev = Msg.current();
        Msg.use("afk");
        try {
            Msg.send(p, "<yellow>+" + amount + " Stars" + (bonus > 0 ? " <dark_gray>(+" + bonus + "% Rang-Bonus)" : "")
                    + " <gray>– Gesamt: <white>" + EconomyManager.fmtC(plugin.economy().crystals(p.getUniqueId())));
        } finally { Msg.use(prev); }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }
}

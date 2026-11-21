package de.smpcore.tools;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Spezial-Tools (Hammer, Excavator ...), die NxN Blöcke auf einmal abbauen. */
public final class ToolManager {
    public record Tool(String id, Material material, String name, List<String> lore, int size, Map<String, Integer> enchants, int model,
                       String mode, int maxBlocks, boolean leaves, boolean requireLeaves, String equipment) {
        public boolean tree() { return "tree".equals(mode); }
    }

    private final SMPCore plugin;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final NamespacedKey key;
    private final NamespacedKey expKey;

    public ToolManager(SMPCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "tool_id");
        this.expKey = new NamespacedKey(plugin, "tool_expires");
    }

    public void load() {
        tools.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "tools.yml"));
        ConfigurationSection s = y.getConfigurationSection("tools");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            Material m = Material.matchMaterial(c.getString("material", "DIAMOND_PICKAXE"));
            if (m == null) { plugin.getLogger().warning("Tool " + id + ": unbekanntes Material"); continue; }
            int size = c.getInt("size", 3);
            if (size % 2 == 0) size++; // nur ungerade Größen (3, 5, 7)
            size = Math.max(1, Math.min(size, 9));
            Map<String, Integer> ench = new LinkedHashMap<>();
            ConfigurationSection es = c.getConfigurationSection("enchants");
            if (es != null) for (String k : es.getKeys(false)) ench.put(k, es.getInt(k));
            tools.put(id.toLowerCase(Locale.ROOT), new Tool(id.toLowerCase(Locale.ROOT), m, c.getString("name", id),
                    c.getStringList("lore"), size, ench, c.getInt("model", 0),
                    c.getString("mode", "area").toLowerCase(Locale.ROOT), Math.max(1, Math.min(500, c.getInt("max-blocks", 200))),
                    c.getBoolean("leaves", true), c.getBoolean("require-leaves", true), c.getString("equipment", "")));
        }
    }

    public Collection<Tool> all() { return tools.values(); }
    public Tool get(String id) { return id == null ? null : tools.get(id.toLowerCase(Locale.ROOT)); }

    public ItemStack create(Tool t, int amount) { return create(t, amount, 0L); }

    /** expiresAt = Zeitpunkt (ms), ab dem das Tool kaputtgeht; 0 = nie. */
    public ItemStack create(Tool t, int amount, long expiresAt) {
        ItemStack it = new ItemStack(t.material(), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(t.name()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : t.lore()) lore.add(Msg.item(l));
        if (t.tree()) lore.add(Msg.item("<dark_gray>Sneaken = nur 1 Block"));
        else if (t.size() > 1) lore.add(Msg.item("<gray>Abbaufläche: <white>" + t.size() + "x" + t.size()));
        if (expiresAt > 0) {
            lore.add(Msg.item("<red>Läuft ab: <white>" + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(expiresAt))));
            meta.getPersistentDataContainer().set(expKey, PersistentDataType.LONG, expiresAt);
        }
        meta.lore(lore);
        for (Map.Entry<String, Integer> e : t.enchants().entrySet()) {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase(Locale.ROOT)));
            if (en != null) meta.addEnchant(en, e.getValue(), true);
        }
        if (t.model() > 0) meta.setCustomModelData(t.model());
        if (!t.equipment().isEmpty()) { // Resource-Pack: eigene Rüstungsoptik am Körper (assets/<ns>/equipment/<name>.json)
            NamespacedKey eqKey = NamespacedKey.fromString(t.equipment());
            if (eqKey != null) {
                var eq = meta.getEquippable();
                eq.setModel(eqKey);
                meta.setEquippable(eq);
            }
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id());
        it.setItemMeta(meta);
        return it;
    }

    public Tool of(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        if (isExpired(it)) return null; // abgelaufene Tools haben keine Spezialfunktion mehr
        String id = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : tools.get(id);
    }

    /** Ablaufzeit des Tools (ms) oder 0, wenn es unbegrenzt ist. */
    public long expiresAt(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return 0L;
        Long v = it.getItemMeta().getPersistentDataContainer().get(expKey, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    public boolean isExpired(ItemStack it) {
        long e = expiresAt(it);
        return e > 0 && System.currentTimeMillis() >= e;
    }

    /** Standard-Haltbarkeit neuer Tools per /smptools give (config tools.default-duration, z. B. 3d; 0 = unbegrenzt). */
    public long defaultDurationMs() {
        long v = parseDuration(plugin.getConfig().getString("tools.default-duration", "3d"));
        return Math.max(0, v);
    }

    /** "3d", "12h", "30m", "1d12h", "90s"; "perm"/"0"/"never" = 0 (unbegrenzt). Ungültig = -1. */
    public static long parseDuration(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("never") || s.equals("off") || s.equals("0")) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([dhms])").matcher(s);
        long total = 0; int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) { case "d" -> 86_400_000L; case "h" -> 3_600_000L; case "m" -> 60_000L; default -> 1_000L; };
            end = m.end();
        }
        return end == s.length() && total > 0 ? total : -1;
    }

    /** 262800000 ms -> "3d 1h" */
    public static String fmtDuration(long ms) {
        long m = Math.max(0, ms / 60_000);
        long d = m / 1440, h = (m % 1440) / 60, mi = m % 60;
        return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + mi + "m" : mi + "m";
    }

    /** Prüft regelmäßig die Inventare: abgelaufene Tools gehen kaputt. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack[] c = p.getInventory().getContents();
                for (int i = 0; i < c.length; i++) {
                    if (!isExpired(c[i])) continue;
                    String id = c[i].getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    Tool t = id == null ? null : tools.get(id);
                    p.getInventory().setItem(i, null);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    p.sendMessage(Msg.c("<red>Dein Tool <white>" + (t == null ? Msg.esc(String.valueOf(id)) : t.name()) + "<red> ist abgelaufen und kaputtgegangen."));
                }
            }
        }, 200L, 200L);
    }

    /** Alle Blöcke der NxN-Fläche senkrecht zur angeklickten Seite. */
    public List<Block> area(Block origin, BlockFace face, int size) {
        int r = size / 2;
        List<Block> out = new ArrayList<>();
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                switch (face) {
                    case UP, DOWN -> out.add(origin.getRelative(a, 0, b));
                    case NORTH, SOUTH -> out.add(origin.getRelative(a, b, 0));
                    default -> out.add(origin.getRelative(0, a, b));
                }
            }
        }
        return out;
    }

    public boolean canBreak(Block b, ItemStack tool) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m == Material.SPAWNER || m.getHardness() < 0) return false;
        if (plugin.data().crateAt(b.getLocation()) != null) return false;
        if (b.getState() instanceof Container) return false;
        String n = tool.getType().name();
        Tag<Material> tag = n.endsWith("_PICKAXE") ? Tag.MINEABLE_PICKAXE
                : n.endsWith("_SHOVEL") ? Tag.MINEABLE_SHOVEL
                : n.endsWith("_AXE") ? Tag.MINEABLE_AXE
                : n.endsWith("_HOE") ? Tag.MINEABLE_HOE : null;
        return tag != null && tag.isTagged(m);
    }
}

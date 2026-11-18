package de.smpcore.enderchest;

import de.smpcore.SMPCore;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Erweiterte Endertruhe (3 bis 6 Reihen je nach Rang). Die Daten liegen in enderchests.yml.
 * Beim ersten Mal wird der Inhalt der normalen Endertruhe übernommen (nichts geht verloren).
 */
public final class EnderChestManager {
    public static final int MAX = 54;

    public static final class Holder implements InventoryHolder {
        public final UUID owner;
        Inventory inv;
        Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, ItemStack[]> data = new HashMap<>();
    private final Map<UUID, Holder> open = new HashMap<>();

    public EnderChestManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "enderchests.yml");
    }

    public void load() {
        data.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("chests");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                ItemStack[] arr = new ItemStack[MAX];
                for (String slot : c.getKeys(false)) {
                    if (!slot.matches("\\d+")) continue;
                    int i = Integer.parseInt(slot);
                    if (i < MAX) arr[i] = Items.decode(c.getString(slot, ""));
                }
                data.put(UUID.fromString(k), arr);
            } catch (Exception ex) {
                plugin.getLogger().warning("Endertruhe von " + k + " konnte nicht geladen werden: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> e : data.entrySet()) {
            String b = "chests." + e.getKey() + ".";
            y.set(b + "v", 1);
            ItemStack[] arr = e.getValue();
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null && !arr[i].getType().isAir()) y.set(b + i, Items.encode(arr[i]));
            }
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    public boolean has(UUID id) { return data.containsKey(id); }

    /** Gespeicherter Inhalt (kann null sein, wenn noch keine Daten vorhanden sind). */
    public ItemStack[] contents(UUID id) {
        ItemStack[] a = data.get(id);
        return a == null ? null : a.clone();
    }

    /** Übernimmt die normale Endertruhe einmalig. */
    public void importVanilla(Player p) {
        if (data.containsKey(p.getUniqueId())) return;
        ItemStack[] arr = new ItemStack[MAX];
        ItemStack[] vc = p.getEnderChest().getContents();
        for (int i = 0; i < vc.length && i < MAX; i++) arr[i] = vc[i];
        data.put(p.getUniqueId(), arr);
        p.getEnderChest().clear();
        save(true);
    }

    public int rows(Player p) { return plugin.ranks().ecRows(p); }

    public boolean open(Player viewer, UUID owner, int rows) {
        if (!data.containsKey(owner)) {
            Player op = Bukkit.getPlayer(owner);
            if (op == null) { Msg.error(viewer, "Dieser Spieler hat noch keine Endertruhen-Daten (er muss einmal online gewesen sein)."); return false; }
            importVanilla(op);
        }
        Holder old = open.get(owner);
        if (old != null && old.inv != null && !old.inv.getViewers().isEmpty()) {
            Msg.error(viewer, "Diese Endertruhe wird gerade benutzt.");
            return false;
        }
        int size = Math.max(1, Math.min(6, rows)) * 9;
        Holder h = new Holder(owner);
        Inventory inv = Bukkit.createInventory(h, size, Msg.c("<dark_purple><bold>Endertruhe <dark_gray>(" + size / 9 + " Reihen)"));
        h.inv = inv;
        ItemStack[] arr = data.get(owner);
        for (int i = 0; i < size; i++) inv.setItem(i, arr[i]);
        open.put(owner, h);
        viewer.openInventory(inv);
        return true;
    }

    public void close(Holder h) {
        ItemStack[] arr = data.get(h.owner);
        if (arr != null) for (int i = 0; i < h.inv.getSize(); i++) arr[i] = h.inv.getItem(i);
        open.remove(h.owner, h);
        save(true);
    }
}

package de.smpcore.link;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.util.Items;
import de.smpcore.util.Msg;
import de.smpcore.web.WebApiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

/**
 * Verknüpfung Minecraft-Account <-> XuroMarket (/verify <code>).
 * Nur verknüpfte Spieler: Inventar und Endertruhe werden (privat) an die Website gesendet, damit sie es dort ansehen können.
 * Mit "/verify logout" kann jeder die Verknüpfung jederzeit lösen (alle Browser-Sitzungen werden beendet, Daten gelöscht).
 */
public final class LinkManager implements Listener {
    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, String> linked = new LinkedHashMap<>();
    private final Map<UUID, Integer> lastHash = new HashMap<>();
    private final Map<UUID, long[]> tries = new HashMap<>(); // uuid -> {Minute, Versuche}

    public LinkManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "linked.yml");
    }

    // ---------------------------------------------------------------- Speichern

    public void load() {
        linked.clear();
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("linked");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { linked.put(UUID.fromString(k), s.getString(k, "?")); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        linked.forEach((id, n) -> y.set("linked." + id, n));
        de.smpcore.util.Persist.write(plugin, file, y.saveToString(), true);
    }

    public boolean isLinked(UUID id) { return linked.containsKey(id); }

    public void start() {
        long sec = Math.max(30, plugin.getConfig().getInt("web-api.push.inventory-seconds", 90));
        Bukkit.getScheduler().runTaskTimer(plugin, this::pushOnline, 20L * 20, 20L * sec);
    }

    // ---------------------------------------------------------------- /verify

    private String siteBase() {
        WebApiManager w = plugin.webApi();
        return w == null ? null : w.marketBase();
    }

    /** Antwort an den Spieler im Server-Thread. */
    private void later(Player p, Runnable r) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) { Msg.use("verify"); try { r.run(); } finally { Msg.reset(); } } });
    }

    public void verify(Player p, String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{6}")) { Msg.error(p, "Ungültiger Code. Den 6-stelligen Code findest du auf der XuroMarket-Seite unter „Mein Konto“."); return; }
        String base = siteBase();
        if (base == null) { Msg.error(p, "XuroMarket ist auf diesem Server nicht eingerichtet (config: web-api.push.market-url)."); return; }
        long minute = System.currentTimeMillis() / 60000L;
        long[] t = tries.computeIfAbsent(p.getUniqueId(), k -> new long[]{minute, 0});
        if (t[0] != minute) { t[0] = minute; t[1] = 0; }
        if (++t[1] > 5) { Msg.error(p, "Zu viele Versuche. Warte eine Minute."); return; }
        String json = "{\"code\":\"" + code + "\",\"uuid\":\"" + p.getUniqueId() + "\",\"name\":" + jstr(p.getName()) + "}";
        Msg.send(p, "<yellow>Prüfe den Code ...");
        plugin.webApi().sendJson(base + "/api/verify", json).whenComplete((r, err) -> later(p, () -> {
            if (err != null) { Msg.error(p, "XuroMarket ist gerade nicht erreichbar. Versuche es gleich noch einmal."); return; }
            switch (r.statusCode()) {
                case 200 -> {
                    linked.put(p.getUniqueId(), p.getName());
                    save();
                    Msg.ok(p, "Verknüpft! Du bist jetzt auf XuroMarket angemeldet und siehst dort dein Inventar und deine Endertruhe.");
                    Msg.send(p, "<gray>Beenden jederzeit mit <white>/verify logout<gray>.");
                    lastHash.remove(p.getUniqueId());
                    pushNow(List.of(p));
                }
                case 404 -> Msg.error(p, "Dieser Code ist unbekannt oder abgelaufen. Erzeuge auf der Seite einen neuen.");
                case 409 -> Msg.error(p, "Dieser Code wurde schon benutzt.");
                case 401, 403 -> { Msg.error(p, "Verknüpfung nicht möglich (Server-Konfiguration). Bitte melde das dem Team."); plugin.getLogger().warning("verify: XuroMarket lehnt den api-key ab (HTTP " + r.statusCode() + ")."); }
                default -> Msg.error(p, "Verknüpfung fehlgeschlagen (HTTP " + r.statusCode() + ").");
            }
        }));
    }

    /** Verknüpfung lösen: Browser-Sitzungen beenden und gespeicherte Daten auf der Website löschen. */
    public void unlink(Player p) {
        boolean was = linked.remove(p.getUniqueId()) != null;
        lastHash.remove(p.getUniqueId());
        save();
        String base = siteBase();
        if (base != null) plugin.webApi().sendJson(base + "/api/revoke", "{\"uuid\":\"" + p.getUniqueId() + "\"}");
        if (was) Msg.ok(p, "Verknüpfung gelöst. Alle Browser-Sitzungen sind beendet und deine Daten werden auf XuroMarket gelöscht.");
        else Msg.send(p, "<gray>Dein Account war nicht verknüpft. Eventuelle alte Sitzungen wurden trotzdem beendet.");
    }

    public void status(Player p) {
        Msg.send(p, isLinked(p.getUniqueId()) ? "<green>Dein Account ist mit XuroMarket verknüpft. <gray>Lösen: <white>/verify logout"
                : "<gray>Nicht verknüpft. Öffne XuroMarket -> „Mein Konto“, klicke auf „Anmelden“ und tippe hier <white>/verify <Code>");
    }

    // ---------------------------------------------------------------- Daten senden

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isLinked(p.getUniqueId()) && !Bukkit.isStopping()) pushNow(List.of(p)); // letzter Stand, bevor er offline ist
    }

    private void pushOnline() {
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (isLinked(p.getUniqueId())) list.add(p);
        if (!list.isEmpty()) pushNow(list);
    }

    private void pushNow(List<Player> players) {
        String base = siteBase();
        if (base == null) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        Map<UUID, Integer> hashes = new HashMap<>();
        for (Player p : players) {
            Map<String, Object> snap = snapshot(p);
            int h = WebApiManager.toJson(snap.get("inventory")).hashCode() * 31 + WebApiManager.toJson(snap.get("enderchest")).hashCode()
                    + WebApiManager.toJson(snap.get("stats")).hashCode() * 7;
            Integer prev = lastHash.get(p.getUniqueId());
            if (prev != null && prev == h) continue; // nichts geändert -> nichts senden (spart Aufrufe)
            snap.put("updated", System.currentTimeMillis());
            batch.add(snap);
            hashes.put(p.getUniqueId(), h);
        }
        if (batch.isEmpty()) return;
        plugin.webApi().sendJson(base + "/api/inventory", WebApiManager.toJson(Map.of("players", batch))).whenComplete((r, err) -> {
            if (err == null && r.statusCode() / 100 == 2) Bukkit.getScheduler().runTask(plugin, () -> lastHash.putAll(hashes));
        });
    }

    private Map<String, Object> snapshot(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", p.getUniqueId().toString());
        m.put("name", p.getName());
        ItemStack[] inv = p.getInventory().getContents(); // 0-35 Inventar, 36-39 Rüstung (Schuhe..Helm), 40 Zweithand
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < inv.length; i++) addItem(items, inv[i], i);
        m.put("inventory", items);
        List<Map<String, Object>> ec = new ArrayList<>();
        int rows = Math.max(1, Math.min(6, plugin.ranks().ecRows(p)));
        ItemStack[] arr = plugin.enderChests().contents(p.getUniqueId());
        if (arr == null) arr = p.getEnderChest().getContents();
        for (int i = 0; i < Math.min(arr.length, rows * 9); i++) addItem(ec, arr[i], i);
        m.put("enderchest", ec);
        m.put("enderRows", rows);
        Map<String, Object> st = new LinkedHashMap<>();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d != null) {
            st.put("money", Math.round(d.money * 100) / 100.0);
            st.put("bank", Math.round(d.bank * 100) / 100.0);
            st.put("crystals", d.crystals);
            st.put("gems", d.gems);
            st.put("rank", d.rank == null ? "default" : d.rank);
        }
        st.put("level", p.getLevel());
        m.put("stats", st);
        return m;
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static void addItem(List<Map<String, Object>> out, ItemStack it, int slot) {
        if (it == null || it.getType().isAir()) return;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("slot", slot);
        e.put("item", it.getType().name());
        e.put("name", Items.pretty(it.getType()));
        e.put("amount", it.getAmount());
        if (it.hasItemMeta()) {
            ItemMeta meta = it.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) e.put("custom", cut(PLAIN.serialize(meta.displayName()), 80));
            Map<Enchantment, Integer> en = meta instanceof EnchantmentStorageMeta esm ? esm.getStoredEnchants() : it.getEnchantments();
            if (!en.isEmpty()) {
                List<String> l = new ArrayList<>();
                en.forEach((k, v) -> l.add(k.getKey().getKey().replace('_', ' ') + " " + v));
                e.put("enchants", l);
            }
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                List<String> l = new ArrayList<>();
                for (Component c : lore) { if (l.size() >= 8) break; l.add(cut(PLAIN.serialize(c), 80)); }
                e.put("lore", l);
            }
            if (meta instanceof Damageable dm && it.getType().getMaxDurability() > 0 && dm.hasDamage()) {
                int max = it.getType().getMaxDurability();
                e.put("durability", Math.max(0, Math.round((max - dm.getDamage()) * 100f / max)));
            }
        }
        out.add(e);
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    private static String jstr(String s) { return WebApiManager.toJson(s); }
}

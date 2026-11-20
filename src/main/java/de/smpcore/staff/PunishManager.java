package de.smpcore.staff;

import de.smpcore.SMPCore;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Bans (dauerhaft/temporär), Strafen-Verlauf für /lookup. Gespeichert in bans.yml und punishments.yml. */
public final class PunishManager implements Listener {
    public static final class Ban {
        public final UUID target;
        public final String name, by, reason;
        public final UUID byId;
        public final long created, until; // until 0 = dauerhaft
        Ban(UUID target, String name, UUID byId, String by, String reason, long created, long until) {
            this.target = target; this.name = name; this.byId = byId; this.by = by; this.reason = reason; this.created = created; this.until = until;
        }
        public boolean expired() { return until > 0 && System.currentTimeMillis() >= until; }
    }

    public record Entry(String type, String by, String reason, long time, long until) {}

    private final SMPCore plugin;
    private final File banFile, histFile;
    private final Map<UUID, Ban> bans = new LinkedHashMap<>();
    private final Map<UUID, List<Entry>> history = new HashMap<>();

    public PunishManager(SMPCore plugin) {
        this.plugin = plugin;
        this.banFile = new File(plugin.getDataFolder(), "bans.yml");
        this.histFile = new File(plugin.getDataFolder(), "punishments.yml");
    }

    // ---------------------------------------------------------------- Speicher
    public void load() {
        bans.clear();
        history.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(banFile).getConfigurationSection("bans");
        if (root != null) for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                UUID id = UUID.fromString(k);
                String byId = c.getString("byId");
                bans.put(id, new Ban(id, c.getString("name", "?"), byId == null ? null : UUID.fromString(byId), c.getString("by", "?"),
                        c.getString("reason", ""), c.getLong("created"), c.getLong("until")));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ban " + k + " ungültig: " + ex.getMessage());
            }
        }
        ConfigurationSection h = YamlConfiguration.loadConfiguration(histFile).getConfigurationSection("history");
        if (h != null) for (String k : h.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                List<Entry> l = new ArrayList<>();
                for (Map<?, ?> m : h.getMapList(k)) {
                    l.add(new Entry(String.valueOf(m.get("type")), String.valueOf(m.get("by")), String.valueOf(m.get("reason")),
                            m.get("time") instanceof Number n ? n.longValue() : 0, m.get("until") instanceof Number u ? u.longValue() : 0));
                }
                history.put(id, l);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (Ban b : bans.values()) {
            String p = "bans." + b.target + ".";
            y.set(p + "name", b.name);
            if (b.byId != null) y.set(p + "byId", b.byId.toString());
            y.set(p + "by", b.by);
            y.set(p + "reason", b.reason);
            y.set(p + "created", b.created);
            y.set(p + "until", b.until);
        }
        Persist.write(plugin, banFile, y.saveToString(), async);
        YamlConfiguration hy = new YamlConfiguration();
        history.forEach((id, list) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Entry e : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", e.type()); m.put("by", e.by()); m.put("reason", e.reason()); m.put("time", e.time()); m.put("until", e.until());
                out.add(m);
            }
            hy.set("history." + id, out);
        });
        Persist.write(plugin, histFile, hy.saveToString(), async);
    }

    // ---------------------------------------------------------------- Abfragen
    /** Aktiver Ban des Spielers (abgelaufene werden entfernt). */
    public Ban get(UUID id) {
        Ban b = bans.get(id);
        if (b != null && b.expired()) {
            bans.remove(id);
            log(id, "AUTO-UNBAN", "System", "Ban abgelaufen", 0);
            save(true);
            return null;
        }
        return b;
    }

    public Collection<Ban> active() {
        new ArrayList<>(bans.keySet()).forEach(this::get); // abgelaufene aufräumen
        return bans.values();
    }

    public List<Entry> history(UUID id) { return history.getOrDefault(id, List.of()); }

    public void log(UUID id, String type, String by, String reason, long until) {
        List<Entry> l = history.computeIfAbsent(id, k -> new ArrayList<>());
        l.add(new Entry(type, by, reason, System.currentTimeMillis(), until));
        while (l.size() > 50) l.remove(0);
    }

    // ---------------------------------------------------------------- Aktionen
    public void ban(UUID id, String name, UUID byId, String by, String reason, long durationMs) {
        long now = System.currentTimeMillis();
        long until = durationMs > 0 ? now + durationMs : 0;
        bans.put(id, new Ban(id, name, byId, by, reason, now, until));
        log(id, durationMs > 0 ? "TEMPBAN" : "BAN", by, reason, until);
        save(true);
        Player p = Bukkit.getPlayer(id);
        if (p != null) p.kick(screen(bans.get(id)));
    }

    public boolean unban(UUID id, String by, String reason) {
        Ban b = bans.remove(id);
        if (b == null) return false;
        log(id, "UNBAN", by, reason, 0);
        save(true);
        return true;
    }

    public static String when(long ms) { return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(ms)); }

    public String remaining(Ban b) { return b.until == 0 ? "dauerhaft" : "noch " + Num.duration(b.until - System.currentTimeMillis()); }

    private net.kyori.adventure.text.Component screen(Ban b) {
        String appeal = plugin.getConfig().getString("ban.appeal-text", "");
        return Msg.c("<red><bold>Du wurdest gebannt!\n\n<gray>Grund: <white>" + Msg.esc(b.reason)
                + "\n<gray>Dauer: <white>" + remaining(b) + "\n<gray>Von: <white>" + Msg.esc(b.by)
                + (appeal.isEmpty() ? "" : "\n\n<dark_gray>" + Msg.esc(appeal)));
    }

    // ---------------------------------------------------------------- Login-Sperre
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent e) {
        Ban b = get(e.getPlayer().getUniqueId());
        if (b != null) e.disallow(PlayerLoginEvent.Result.KICK_BANNED, screen(b));
    }
}

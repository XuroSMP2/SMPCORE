package de.smpcore.manhunt;

import de.smpcore.SMPCore;
import de.smpcore.economy.EconomyManager;
import de.smpcore.tools.ToolManager;
import de.smpcore.util.Msg;
import de.smpcore.util.Num;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.util.*;

/**
 * /manhunt <Spieler> <Preis> <Währung> <Zeit>: Jemand setzt ein Preisgeld auf einen Spieler. Stirbt der Spieler innerhalb
 * der Zeit, wird die Damage-Rangliste (Top 3) im Chat gezeigt und das Preisgeld an die Top-Damage-Dealer ausgezahlt.
 * Läuft die Zeit ab, bekommt der Auftraggeber sein Geld zurück.
 */
public final class ManhuntManager implements Listener {
    public static final class Hunt {
        public final UUID target, creator;
        public final String targetName, creatorName;
        public final long prize, endsAt;
        public final String currency;
        public final Map<UUID, Double> damage = new LinkedHashMap<>();
        public final Map<UUID, String> names = new HashMap<>();

        Hunt(UUID target, String targetName, UUID creator, String creatorName, long prize, String currency, long endsAt) {
            this.target = target; this.targetName = targetName; this.creator = creator; this.creatorName = creatorName;
            this.prize = prize; this.currency = currency; this.endsAt = endsAt;
        }
    }

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Hunt> hunts = new LinkedHashMap<>(); // Ziel -> Hunt
    private boolean dirty;

    public ManhuntManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "manhunts.yml");
    }

    // ---------------------------------------------------------------- Speicher
    public void load() {
        hunts.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("hunts");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Hunt h = new Hunt(UUID.fromString(k), c.getString("name", "?"), UUID.fromString(c.getString("creator", "")),
                        c.getString("creatorName", "?"), c.getLong("prize"), c.getString("currency", "MONEY"), c.getLong("endsAt"));
                ConfigurationSection d = c.getConfigurationSection("damage");
                if (d != null) for (String u : d.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(u);
                        h.damage.put(id, d.getDouble(u + ".dmg"));
                        h.names.put(id, d.getString(u + ".name", "?"));
                    } catch (IllegalArgumentException ignored) { }
                }
                hunts.put(h.target, h);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Manhunt " + k + " ungültig: " + ex.getMessage());
            }
        }
    }

    public void save(boolean async) {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Hunt h : hunts.values()) {
            String b = "hunts." + h.target + ".";
            y.set(b + "name", h.targetName);
            y.set(b + "creator", h.creator.toString());
            y.set(b + "creatorName", h.creatorName);
            y.set(b + "prize", h.prize);
            y.set(b + "currency", h.currency);
            y.set(b + "endsAt", h.endsAt);
            h.damage.forEach((u, dmg) -> {
                y.set(b + "damage." + u + ".dmg", dmg);
                y.set(b + "damage." + u + ".name", h.names.getOrDefault(u, "?"));
            });
        }
        Persist.write(plugin, file, y.saveToString(), async);
    }

    /** Läuft jede Sekunde-Gruppe: abgelaufene Hunts beenden (Rückerstattung), Änderungen speichern. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Hunt h : new ArrayList<>(hunts.values())) {
                if (now < h.endsAt) continue;
                hunts.remove(h.target);
                refund(h);
                dirty = true;
                Bukkit.broadcast(Msg.c(TAG + "<gray>Das Manhunt auf <red>" + Msg.esc(h.targetName) + " <gray>ist abgelaufen. <dark_gray>(" + Msg.esc(h.creatorName) + " bekommt sein Geld zurück)"));
            }
            if (dirty) save(true);
        }, 100L, 100L);
    }

    private static final String TAG = "<dark_gray>[<red>Manhunt<dark_gray>] ";

    public Collection<Hunt> all() { return hunts.values(); }
    public Hunt of(UUID target) { return hunts.get(target); }

    // ---------------------------------------------------------------- Währungen
    public static String currencyOf(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "money", "geld", "coins", "cash", "$" -> "MONEY";
            case "stars", "star", "sterne", "crystals", "crystal", "kristalle" -> "STARS";
            case "gems", "gem", "edelsteine" -> "GEMS";
            default -> null;
        };
    }

    public static String fmt(String currency, long amount) {
        return switch (currency) { case "STARS" -> EconomyManager.fmtC(amount); case "GEMS" -> EconomyManager.fmtG(amount); default -> EconomyManager.fmt(amount); };
    }

    private boolean take(UUID id, String cur, long a) {
        return switch (cur) {
            case "STARS" -> plugin.economy().takeCrystals(id, a);
            case "GEMS" -> plugin.economy().takeGems(id, a);
            default -> plugin.economy().take(id, a);
        };
    }

    private void give(UUID id, String cur, long a) {
        if (a <= 0) return;
        switch (cur) {
            case "STARS" -> plugin.economy().addCrystals(id, a);
            case "GEMS" -> plugin.economy().addGems(id, a);
            default -> plugin.economy().add(id, a);
        }
    }

    private void refund(Hunt h) { give(h.creator, h.currency, h.prize); }

    // ---------------------------------------------------------------- Befehle
    public void create(Player p, String targetArg, String priceArg, String curArg, String timeArg) {
        Player t = Bukkit.getPlayerExact(targetArg);
        if (t == null) { Msg.error(p, "Der Spieler muss online sein."); return; }
        if (t.getUniqueId().equals(p.getUniqueId())) { Msg.error(p, "Du kannst kein Manhunt auf dich selbst starten."); return; }
        if (hunts.containsKey(t.getUniqueId())) { Msg.error(p, "Auf " + Msg.esc(t.getName()) + " läuft schon ein Manhunt."); return; }
        String cur = currencyOf(curArg);
        if (cur == null) { Msg.error(p, "Währung: money, stars oder gems."); return; }
        long prize;
        try { prize = (long) Math.floor(Num.parse(priceArg)); } catch (NumberFormatException ex) { Msg.error(p, "Ungültiger Preis."); return; }
        long min = plugin.getConfig().getLong("manhunt.min-" + cur.toLowerCase(Locale.ROOT), cur.equals("MONEY") ? 1000 : 10);
        if (prize < min) { Msg.error(p, "Mindestpreis: " + fmt(cur, min)); return; }
        long ms = ToolManager.parseDuration(timeArg);
        long minMs = 60_000L;
        long maxMs = Math.max(minMs, Math.max(0, ToolManager.parseDuration(plugin.getConfig().getString("manhunt.max-duration", "1d"))));
        if (ms < minMs || ms > maxMs) { Msg.error(p, "Zeit: mindestens 1m, höchstens " + ToolManager.fmtDuration(maxMs) + " (z. B. 30m, 2h)."); return; }
        if (!take(p.getUniqueId(), cur, prize)) { Msg.error(p, "Dafür fehlt dir etwas: " + fmt(cur, prize) + " nötig."); return; }

        Hunt h = new Hunt(t.getUniqueId(), t.getName(), p.getUniqueId(), p.getName(), prize, cur, System.currentTimeMillis() + ms);
        hunts.put(h.target, h);
        save(true);
        Bukkit.broadcast(Msg.c(TAG + "<white>" + Msg.esc(p.getName()) + " <gray>hat ein Manhunt auf <red><bold>" + Msg.esc(t.getName())
                + "<reset> <gray>gestartet! Preis: <green>" + fmt(cur, prize) + " <gray>für den meisten Damage. <dark_gray>(" + ToolManager.fmtDuration(ms) + ")"));
    }

    /** Bricht ein Manhunt ab (Auftraggeber oder Admin); das Geld geht zurück an den Auftraggeber. */
    public boolean cancel(Player by, String targetName) {
        for (Hunt h : new ArrayList<>(hunts.values())) {
            if (!h.targetName.equalsIgnoreCase(targetName)) continue;
            if (!h.creator.equals(by.getUniqueId()) && !by.hasPermission("smpcore.admin")) { Msg.error(by, "Nur der Auftraggeber oder ein Admin kann das abbrechen."); return true; }
            hunts.remove(h.target);
            refund(h);
            save(true);
            Msg.ok(by, "Manhunt auf " + Msg.esc(h.targetName) + " abgebrochen. Das Geld wurde an " + Msg.esc(h.creatorName) + " zurückgegeben.");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Hunt h = hunts.get(victim.getUniqueId());
        if (h == null) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
        if (!(src instanceof Player dmgr) || dmgr.getUniqueId().equals(victim.getUniqueId())) return;
        double dmg = Math.min(e.getFinalDamage(), victim.getHealth()); // Overkill zählt nicht
        if (dmg <= 0) return;
        h.damage.merge(dmgr.getUniqueId(), dmg, Double::sum);
        h.names.put(dmgr.getUniqueId(), dmgr.getName());
        dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Hunt h = hunts.remove(e.getEntity().getUniqueId());
        if (h == null) return;
        save(true);

        List<Map.Entry<UUID, Double>> top = new ArrayList<>(h.damage.entrySet());
        top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        if (top.size() > 3) top = top.subList(0, 3);

        if (top.isEmpty()) { // niemand hat Damage gemacht (z. B. Sturz): Geld zurück
            refund(h);
            Bukkit.broadcast(Msg.c(TAG + "<red>" + Msg.esc(h.targetName) + " <gray>ist gestorben, aber niemand hat Damage gemacht. <dark_gray>(" + Msg.esc(h.creatorName) + " bekommt sein Geld zurück)"));
            return;
        }

        // Aufteilung des Preisgelds auf die vorhandenen Plätze (config manhunt.payout-split, Standard 50/30/20)
        List<Integer> split = plugin.getConfig().getIntegerList("manhunt.payout-split");
        if (split.isEmpty()) split = List.of(50, 30, 20);
        int n = top.size();
        int[] w = new int[n];
        int sum = 0;
        for (int i = 0; i < n; i++) { w[i] = i < split.size() ? Math.max(0, split.get(i)) : 0; sum += w[i]; }
        if (sum <= 0) { w[0] = 1; sum = 1; }
        long[] reward = new long[n];
        long paid = 0;
        for (int i = 0; i < n; i++) { reward[i] = (long) Math.floor(h.prize * (w[i] / (double) sum)); paid += reward[i]; }
        reward[0] += Math.max(0, h.prize - paid); // Rundungsrest an Platz 1

        StringBuilder sb = new StringBuilder(TAG + "<gold><bold>Am meisten Damage an " + Msg.esc(h.targetName) + " Rangliste:</bold>");
        for (int i = 0; i < n; i++) {
            UUID id = top.get(i).getKey();
            give(id, h.currency, reward[i]);
            String hearts = String.format(Locale.GERMAN, "%.1f", top.get(i).getValue() / 2.0);
            sb.append("\n<yellow>").append(i + 1).append(". <white>").append(Msg.esc(h.names.getOrDefault(id, "?")))
                    .append(" <dark_gray>- <red>").append(hearts).append(" ❤")
                    .append(reward[i] > 0 ? " <dark_gray>(<green>+" + fmt(h.currency, reward[i]) + "<dark_gray>)" : "");
            Player online = Bukkit.getPlayer(id);
            if (online != null && reward[i] > 0) online.playSound(online.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        Bukkit.broadcast(Msg.c(sb.toString()));
    }
}

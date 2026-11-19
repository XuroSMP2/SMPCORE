package de.smpcore.spoof;

import de.smpcore.SMPCore;
import de.smpcore.data.PlayerData;
import de.smpcore.rank.RankManager;
import de.smpcore.util.Items;
import de.smpcore.util.Locs;
import de.smpcore.util.Msg;
import de.smpcore.util.Persist;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /spoof: Der Owner übernimmt zeitweise den Account eines Spielers - nur auf diesem Server.
 * Der Spieler ist währenddessen ausgesperrt. Mit /spoof off kommt der Owner mit seinem eigenen Zustand zurück,
 * der veränderte Zustand wird dem Spieler beim nächsten Beitritt übergeben.
 *
 * Technik: Der Minecraft-Name/UUID des Admins bleibt (technisch nicht änderbar). Stattdessen werden
 * Plugin-Daten (Geld, Rang, Homes, Endertruhe, Clan ...) über einen Alias auf den Zieldatensatz umgeleitet
 * und Inventar, XP, Leben, Hunger, Effekte, Gamemode und Position aus dem Ziel-Zustand übernommen.
 */
public final class SpoofManager implements Listener {

    // ---------- Spielzustand ----------
    static final class State {
        ItemStack[] inv = new ItemStack[41];
        int level, food;
        float exp, sat;
        double health = 20;
        GameMode gm = GameMode.SURVIVAL;
        Location loc;
        boolean allowFlight, flying;
        List<PotionEffect> effects = new ArrayList<>();

        static State capture(Player p) {
            State s = new State();
            s.inv = p.getInventory().getContents().clone();
            s.level = p.getLevel();
            s.exp = p.getExp();
            s.health = p.getHealth();
            s.food = p.getFoodLevel();
            s.sat = p.getSaturation();
            s.gm = p.getGameMode();
            s.loc = p.getLocation().clone();
            s.allowFlight = p.getAllowFlight();
            s.flying = p.isFlying();
            s.effects = new ArrayList<>(p.getActivePotionEffects());
            return s;
        }

        @SuppressWarnings("deprecation")
        void apply(Player p) {
            p.getInventory().setContents(inv);
            p.setLevel(Math.max(0, level));
            p.setExp(Math.max(0f, Math.min(0.999f, exp)));
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
            for (PotionEffect e : effects) p.addPotionEffect(e);
            p.setGameMode(gm);
            p.setAllowFlight(allowFlight || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
            if (p.getAllowFlight()) p.setFlying(flying);
            p.setFoodLevel(Math.max(0, Math.min(20, food)));
            p.setSaturation(Math.max(0f, sat));
            p.setHealth(Math.max(1.0, Math.min(health, p.getMaxHealth())));
            p.setFireTicks(0);
            p.setFallDistance(0f);
            if (loc != null && loc.getWorld() != null) p.teleportAsync(loc);
        }

        void write(ConfigurationSection c) {
            c.set("size", inv.length);
            for (int i = 0; i < inv.length; i++) {
                if (inv[i] != null && !inv[i].getType().isAir()) c.set("inv." + i, Items.encode(inv[i]));
            }
            c.set("level", level);
            c.set("exp", (double) exp);
            c.set("health", health);
            c.set("food", food);
            c.set("sat", (double) sat);
            c.set("gm", gm.name());
            if (loc != null && loc.getWorld() != null) c.set("loc", Locs.str(loc));
            c.set("allowFlight", allowFlight);
            c.set("flying", flying);
            c.set("effects", effects);
        }

        static State read(ConfigurationSection c) {
            State s = new State();
            s.inv = new ItemStack[Math.max(41, c.getInt("size", 41))];
            ConfigurationSection inv = c.getConfigurationSection("inv");
            if (inv != null) {
                for (String k : inv.getKeys(false)) {
                    try {
                        int i = Integer.parseInt(k);
                        if (i >= 0 && i < s.inv.length) s.inv[i] = Items.decode(inv.getString(k, ""));
                    } catch (Exception ignored) { }
                }
            }
            s.level = c.getInt("level");
            s.exp = (float) c.getDouble("exp");
            s.health = c.getDouble("health", 20);
            s.food = c.getInt("food", 20);
            s.sat = (float) c.getDouble("sat", 5);
            try { s.gm = GameMode.valueOf(c.getString("gm", "SURVIVAL")); } catch (IllegalArgumentException ignored) { }
            String l = c.getString("loc");
            s.loc = l == null ? null : Locs.parse(l);
            s.allowFlight = c.getBoolean("allowFlight");
            s.flying = c.getBoolean("flying");
            List<?> raw = c.getList("effects");
            if (raw != null) for (Object o : raw) if (o instanceof PotionEffect e) s.effects.add(e);
            return s;
        }
    }

    // ---------- aktive Spoofs ----------
    static final class Rec {
        UUID admin, target;
        String adminName, targetName;
        boolean wasOp, released;
        long since;
        State backup;
    }

    private final SMPCore plugin;
    private final File activeFile, snapDir, logFile;
    private final Map<UUID, Rec> byAdmin = new HashMap<>();
    private final Map<UUID, Rec> byTarget = new HashMap<>();

    public SpoofManager(SMPCore plugin) {
        this.plugin = plugin;
        File dir = plugin.getDataFolder();
        activeFile = new File(dir, "spoof-active.yml");
        snapDir = new File(dir, "spoof-snapshots");
        logFile = new File(dir, "spoof.log");
    }

    public void load() {
        snapDir.mkdirs();
        byAdmin.clear();
        byTarget.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(activeFile).getConfigurationSection("active");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            try {
                ConfigurationSection c = root.getConfigurationSection(k);
                if (c == null) continue;
                Rec r = new Rec();
                r.admin = UUID.fromString(k);
                r.target = UUID.fromString(c.getString("target", ""));
                r.adminName = c.getString("adminName", "?");
                r.targetName = c.getString("targetName", "?");
                r.wasOp = c.getBoolean("wasOp");
                r.released = c.getBoolean("released");
                r.since = c.getLong("since");
                ConfigurationSection b = c.getConfigurationSection("backup");
                r.backup = b == null ? new State() : State.read(b);
                byAdmin.put(r.admin, r);
                if (!r.released) byTarget.put(r.target, r);
            } catch (Exception ex) {
                plugin.getLogger().warning("Spoof-Eintrag " + k + " ungültig: " + ex.getMessage());
            }
        }
        if (!byAdmin.isEmpty()) plugin.getLogger().warning("Offene Spoofs gefunden (" + byAdmin.size() + "). Sie werden beendet, sobald der Admin beitritt.");
    }

    private void saveActive() {
        YamlConfiguration y = new YamlConfiguration();
        for (Rec r : byAdmin.values()) {
            String b = "active." + r.admin + ".";
            y.set(b + "target", r.target.toString());
            y.set(b + "adminName", r.adminName);
            y.set(b + "targetName", r.targetName);
            y.set(b + "wasOp", r.wasOp);
            y.set(b + "released", r.released);
            y.set(b + "since", r.since);
            r.backup.write(y.createSection(b + "backup"));
        }
        Persist.write(plugin, activeFile, y.saveToString(), false);
    }

    /** Leitet Daten-Zugriffe des spoofenden Admins auf den Ziel-Datensatz um. */
    public UUID resolve(UUID id) {
        Rec r = byAdmin.get(id);
        return r == null ? id : r.target;
    }

    public boolean isSpoofing(UUID admin) { return byAdmin.containsKey(admin); }
    public boolean isBlocked(UUID target) { return byTarget.containsKey(target); }
    public String targetName(UUID admin) { Rec r = byAdmin.get(admin); return r == null ? null : r.targetName; }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Rec r : byAdmin.values()) {
            long min = (System.currentTimeMillis() - r.since) / 60_000;
            out.add(r.adminName + " → " + r.targetName + " (seit " + min + " Min." + (r.released ? ", freigegeben" : "") + ")");
        }
        return out;
    }

    // ---------- Start ----------
    public void start(Player admin, String targetArg) {
        if (byAdmin.containsKey(admin.getUniqueId())) { Msg.error(admin, "Du spoofst schon <white>" + Msg.esc(targetName(admin.getUniqueId())) + "<red>. Beende das mit /spoof off."); return; }
        List<String> allowed = plugin.getConfig().getStringList("spoof.allowed-players");
        if (!allowed.isEmpty() && allowed.stream().noneMatch(n -> n.equalsIgnoreCase(admin.getName()))) {
            Msg.error(admin, "Du bist für /spoof nicht freigeschaltet (spoof.allowed-players)."); return;
        }
        Player online = Bukkit.getPlayerExact(targetArg);
        UUID tid; String tname;
        if (online != null) { tid = online.getUniqueId(); tname = online.getName(); }
        else {
            PlayerData d = plugin.data().find(targetArg);
            if (d == null) { Msg.error(admin, "Spieler <white>" + Msg.esc(targetArg) + "<red> ist nicht bekannt."); return; }
            tid = d.uuid; tname = d.name;
        }
        if (tid.equals(admin.getUniqueId())) { Msg.error(admin, "Du kannst dich nicht selbst spoofen."); return; }
        if (byTarget.containsKey(tid)) { Msg.error(admin, "Dieser Spieler wird schon gespooft."); return; }
        if (byAdmin.containsKey(tid)) { Msg.error(admin, "Dieser Spieler spooft gerade selbst jemanden."); return; }
        if (isProtected(tid, online) && !admin.hasPermission("smpcore.spoof.admin")) {
            Msg.error(admin, "Dieser Spieler ist geschützt (Team-Rang/OP)."); return;
        }
        State target;
        if (online != null) target = State.capture(online);
        else {
            target = readSnapshot(tid);
            if (target == null) {
                Msg.error(admin, "Von <white>" + Msg.esc(tname) + "<red> gibt es noch keinen gespeicherten Zustand. Er muss nach dem Plugin-Update einmal online gewesen sein.");
                return;
            }
        }
        if (target.loc == null) {
            Msg.error(admin, "Die gespeicherte Position von <white>" + Msg.esc(tname) + "<red> ist ungültig (Welt fehlt)."); return;
        }

        Rec r = new Rec();
        r.admin = admin.getUniqueId();
        r.adminName = admin.getName();
        r.target = tid;
        r.targetName = tname;
        r.wasOp = admin.isOp();
        r.since = System.currentTimeMillis();
        r.backup = State.capture(admin);
        byAdmin.put(r.admin, r);
        byTarget.put(tid, r);
        saveActive();                       // zuerst sichern - falls der Server abstürzt
        writeSnapshot(tid, target, false);  // Ausgangszustand des Ziels (für /spoof release)

        if (online != null) {
            online.kick(Msg.c("<red><bold>Dein Account wird gerade vom Serverteam verwendet.\n<gray>Bitte versuche es später erneut."));
        }
        admin.closeInventory();
        target.apply(admin);
        admin.setOp(true);
        applyIdentity(admin, tname);
        log(admin.getName() + " spooft " + tname + " (" + tid + ")");
        Msg.ok(admin, "Du bist jetzt <white>" + Msg.esc(tname) + "<green>. <gray>Zurück mit <white>/spoof off<gray>. <dark_gray>(" + Msg.esc(tname) + " ist gesperrt, solange du spoofst.)");
    }

    private boolean isProtected(UUID tid, Player online) {
        if (online != null && (online.isOp() || online.hasPermission("smpcore.admin"))) return true;
        if (Bukkit.getOfflinePlayer(tid).isOp()) return true;
        List<String> prot = plugin.getConfig().getStringList("spoof.protected-ranks");
        RankManager.Rank rk = plugin.ranks().rankOf(tid);
        return rk != null && prot.stream().anyMatch(x -> x.equalsIgnoreCase(rk.id));
    }

    private void applyIdentity(Player p, String displayName) {
        p.displayName(displayName == null ? null : Msg.c(Msg.esc(displayName)));
        plugin.ranks().apply(p);
        if (displayName != null) {
            RankManager.Rank rk = plugin.ranks().rankOf(p.getUniqueId());
            p.playerListName(Msg.c((rk == null ? "" : rk.prefix) + Msg.esc(displayName)));
        } else {
            p.playerListName(null);
            plugin.ranks().apply(p);
        }
    }

    // ---------- Ende ----------
    /** Beendet den Spoof des Admins. Gibt false zurück, wenn er keinen hat. */
    public boolean finish(Player admin, boolean silent) {
        Rec r = byAdmin.get(admin.getUniqueId());
        if (r == null) return false;
        if (!r.released) writeSnapshot(r.target, State.capture(admin), true); // Zustand geht an den Spieler zurück
        byAdmin.remove(r.admin);
        byTarget.remove(r.target);
        saveActive();
        admin.closeInventory();
        r.backup.apply(admin);
        admin.setOp(r.wasOp);
        applyIdentity(admin, null);
        log(admin.getName() + " beendet Spoof von " + r.targetName + (r.released ? " (freigegeben)" : ""));
        if (!silent) Msg.ok(admin, "Du bist wieder du selbst. <white>" + Msg.esc(r.targetName) + "<green> kann wieder beitreten.");
        return true;
    }

    /** Gibt einen Spieler vorzeitig frei (z. B. Admin offline/abgestürzt). Zustand des Spielers bleibt der Ausgangszustand. */
    public String release(String name) {
        for (Rec r : new ArrayList<>(byAdmin.values())) {
            if (!r.targetName.equalsIgnoreCase(name)) continue;
            r.released = true;
            byTarget.remove(r.target);
            saveActive();
            log("Freigabe von " + r.targetName + " (Admin " + r.adminName + ")");
            Player a = Bukkit.getPlayer(r.admin);
            if (a != null) finish(a, false);
            return r.targetName;
        }
        return null;
    }

    // ---------- Snapshots ----------
    private File snapFile(UUID id) { return new File(snapDir, id + ".yml"); }
    private File pendingFile(UUID id) { return new File(snapDir, id + ".pending"); }

    private void writeSnapshot(UUID id, State s, boolean pending) {
        snapDir.mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        s.write(y.createSection("state"));
        Persist.write(plugin, snapFile(id), y.saveToString(), false);
        try {
            if (pending) Files.writeString(pendingFile(id).toPath(), "1", StandardCharsets.UTF_8);
            else Files.deleteIfExists(pendingFile(id).toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Spoof-Marker: " + e.getMessage());
        }
    }

    private State readSnapshot(UUID id) {
        File f = snapFile(id);
        if (!f.exists()) return null;
        ConfigurationSection c = YamlConfiguration.loadConfiguration(f).getConfigurationSection("state");
        return c == null ? null : State.read(c);
    }

    // ---------- Events ----------
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent e) {
        if (byTarget.containsKey(e.getPlayer().getUniqueId())) {
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    Msg.c("<red><bold>Dein Account wird gerade vom Serverteam verwendet.\n<gray>Bitte versuche es später erneut."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (byAdmin.containsKey(id)) {
            // Admin war beim Neustart/Absturz noch gespooft: alles zurücksetzen
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && finish(p, true)) Msg.send(p, "<yellow>Dein alter Spoof wurde beendet, du bist wieder du selbst.");
            }, 5L);
            return;
        }
        if (pendingFile(id).exists()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                State s = readSnapshot(id);
                if (s != null) {
                    s.apply(p);
                    Msg.send(p, "<gray>Dein Zustand wurde vom Serverteam übernommen und wiederhergestellt.");
                }
                try { Files.deleteIfExists(pendingFile(id).toPath()); } catch (IOException ignored) { }
            }, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Rec r = byAdmin.get(id);
        if (r != null) {
            // Admin geht offline: OP sofort zurücknehmen; Wiederherstellung beim nächsten Beitritt
            p.setOp(r.wasOp);
            return;
        }
        if (byTarget.containsKey(id)) return;      // gerade gekickt, Zustand ist schon gesichert
        if (pendingFile(id).exists()) return;      // übergebener Zustand wartet noch auf Anwendung
        if (plugin.isEnabled()) {
            YamlConfiguration y = new YamlConfiguration();
            State.capture(p).write(y.createSection("state"));
            Persist.write(plugin, snapFile(id), y.saveToString(), true);
        }
    }

    /** Beim Herunterfahren: OP-Status aller spoofenden Admins zurücksetzen. */
    public void shutdown() {
        for (Rec r : byAdmin.values()) {
            Player a = Bukkit.getPlayer(r.admin);
            if (a != null) a.setOp(r.wasOp);
        }
    }

    // ---------- Protokoll ----------
    private void log(String msg) {
        plugin.getLogger().warning("[SPOOF] " + msg);
        try {
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + msg + System.lineSeparator();
            Files.writeString(logFile.toPath(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }
}
